package com.ktb.moyeota.global.external.firebase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.google.firebase.messaging.BatchResponse;
import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.FirebaseMessagingException;
import com.google.firebase.messaging.MessagingErrorCode;
import com.google.firebase.messaging.MulticastMessage;
import com.google.firebase.messaging.SendResponse;
import com.ktb.moyeota.domain.notification.push.PushMessage;
import com.ktb.moyeota.domain.notification.push.PushSendResult;
import com.ktb.moyeota.domain.notification.push.TokenSendOutcome;
import com.ktb.moyeota.domain.notification.push.TokenSendResult;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

class FcmPushSenderTest {

    private static final PushMessage MESSAGE =
            new PushMessage("모여타", "민준님이 동승을 요청했어요", Map.of("notification_id", "1"));

    @Nested
    @DisplayName("toOutcome (FCM 오류 코드 → 토큰별 결과)")
    class ToOutcome {

        @Test
        @DisplayName("UNREGISTERED는 토큰 삭제 대상이다")
        void unregistered() {
            assertThat(FcmPushSender.toOutcome(MessagingErrorCode.UNREGISTERED))
                    .isEqualTo(TokenSendOutcome.UNREGISTERED);
        }

        @ParameterizedTest
        @EnumSource(value = MessagingErrorCode.class, names = {"UNAVAILABLE", "INTERNAL", "QUOTA_EXCEEDED"})
        @DisplayName("FCM 일시 장애는 재시도 대상이다")
        void transientFailure(MessagingErrorCode code) {
            assertThat(FcmPushSender.toOutcome(code)).isEqualTo(TokenSendOutcome.TRANSIENT_FAILURE);
        }

        @Test
        @DisplayName("오류 코드가 없으면(네트워크·타임아웃) 재시도 대상이다")
        void noCode() {
            assertThat(FcmPushSender.toOutcome(null)).isEqualTo(TokenSendOutcome.TRANSIENT_FAILURE);
        }

        @ParameterizedTest
        @EnumSource(value = MessagingErrorCode.class,
                names = {"INVALID_ARGUMENT", "SENDER_ID_MISMATCH", "THIRD_PARTY_AUTH_ERROR"})
        @DisplayName("재시도해도 실패하는 오류는 토큰을 지우지 않고 포기 대상이다")
        void permanentFailure(MessagingErrorCode code) {
            assertThat(FcmPushSender.toOutcome(code)).isEqualTo(TokenSendOutcome.PERMANENT_FAILURE);
        }
    }

    @Nested
    @DisplayName("send")
    class Send {

        private final FirebaseMessaging firebaseMessaging = mock(FirebaseMessaging.class);
        private final FcmPushSender sender = new FcmPushSender(firebaseMessaging);

        @Test
        @DisplayName("토큰 순서대로 응답을 토큰별 결과로 바꾼다")
        void mapsResponsesInOrder() throws Exception {
            BatchResponse batch = batchOf(
                    success(),
                    failure(MessagingErrorCode.UNREGISTERED),
                    failure(MessagingErrorCode.UNAVAILABLE));
            given(firebaseMessaging.sendEachForMulticast(any(MulticastMessage.class))).willReturn(batch);

            PushSendResult result = sender.send(List.of("phone", "old", "pc"), MESSAGE);

            assertThat(result.results()).extracting(TokenSendResult::token).containsExactly("phone", "old", "pc");
            assertThat(result.results()).extracting(TokenSendResult::outcome).containsExactly(
                    TokenSendOutcome.SUCCESS, TokenSendOutcome.UNREGISTERED, TokenSendOutcome.TRANSIENT_FAILURE);
            assertThat(result.results()).extracting(TokenSendResult::errorCode)
                    .containsExactly(null, "UNREGISTERED", "UNAVAILABLE");
        }

        @Test
        @DisplayName("요청 전체가 실패하면 모든 토큰을 일시 오류로 돌려주고 예외를 던지지 않는다")
        void wholeRequestFailed() throws Exception {
            FirebaseMessagingException exception = mock(FirebaseMessagingException.class);
            given(exception.getMessagingErrorCode()).willReturn(MessagingErrorCode.UNAVAILABLE);
            given(firebaseMessaging.sendEachForMulticast(any(MulticastMessage.class))).willThrow(exception);

            PushSendResult result = sender.send(List.of("a", "b"), MESSAGE);

            assertThat(result.results()).allMatch(r -> r.outcome() == TokenSendOutcome.TRANSIENT_FAILURE);
            assertThat(result.errorSummary()).isEqualTo("UNAVAILABLE");
        }

        @Test
        @DisplayName("토큰이 500개를 넘으면 500개씩 나눠 보낸다")
        void chunks() throws Exception {
            BatchResponse first = batchOf(IntStream.range(0, FcmPushSender.MAX_TOKENS_PER_REQUEST)
                    .mapToObj(i -> success()).toArray(SendResponse[]::new));
            BatchResponse second = batchOf(success());
            given(firebaseMessaging.sendEachForMulticast(any(MulticastMessage.class))).willReturn(first, second);
            List<String> tokens = IntStream.range(0, FcmPushSender.MAX_TOKENS_PER_REQUEST + 1)
                    .mapToObj(i -> "t" + i).toList();

            PushSendResult result = sender.send(tokens, MESSAGE);

            verify(firebaseMessaging, times(2)).sendEachForMulticast(any(MulticastMessage.class));
            assertThat(result.results()).hasSize(FcmPushSender.MAX_TOKENS_PER_REQUEST + 1);
            assertThat(result.anySuccess()).isTrue();
        }
    }

    private static BatchResponse batchOf(SendResponse... responses) {
        BatchResponse batch = mock(BatchResponse.class);
        given(batch.getResponses()).willReturn(List.of(responses));
        return batch;
    }

    private static SendResponse success() {
        SendResponse response = mock(SendResponse.class);
        given(response.isSuccessful()).willReturn(true);
        return response;
    }

    private static SendResponse failure(MessagingErrorCode code) {
        FirebaseMessagingException exception = mock(FirebaseMessagingException.class);
        given(exception.getMessagingErrorCode()).willReturn(code);
        SendResponse response = mock(SendResponse.class);
        given(response.isSuccessful()).willReturn(false);
        given(response.getException()).willReturn(exception);
        return response;
    }
}
