package com.ktb.moyeota.domain.user.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.ktb.moyeota.domain.user.dto.PushTokenRequest;
import com.ktb.moyeota.domain.user.error.PushTokenErrorCode;
import com.ktb.moyeota.domain.user.repository.PushTokenRepository;
import com.ktb.moyeota.global.exception.BusinessException;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class PushTokenServiceTest {

    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 12, 9, 0, 0, 123_456_789);
    private static final Long USER_ID = 42L;

    private final PushTokenRepository repository = mock(PushTokenRepository.class);
    private final PushTokenService service = new PushTokenService(
            repository, Clock.fixed(NOW.atZone(SEOUL).toInstant(), SEOUL));

    @Nested
    @DisplayName("register (등록/갱신)")
    class Register {

        @Test
        @DisplayName("토큰을 UPSERT한다 (시각은 마이크로초까지)")
        void upsert() {
            service.register(USER_ID, new PushTokenRequest("fcm-token"));

            verify(repository).upsert(USER_ID, "fcm-token", LocalDateTime.of(2026, 10, 12, 9, 0, 0, 123_456_000));
        }

        @ParameterizedTest
        @NullAndEmptySource
        @ValueSource(strings = {"   "})
        @DisplayName("토큰이 비어 있으면 400 INVALID_PUSH_TOKEN이고 저장하지 않는다")
        void blank(String token) {
            assertThatThrownBy(() -> service.register(USER_ID, new PushTokenRequest(token)))
                    .isInstanceOf(BusinessException.class)
                    .extracting("errorCode").isEqualTo(PushTokenErrorCode.INVALID_PUSH_TOKEN);

            verifyNoInteractions(repository);
        }

        @Test
        @DisplayName("토큰이 255자를 넘으면 400 INVALID_PUSH_TOKEN이고 저장하지 않는다")
        void tooLong() {
            assertThatThrownBy(() -> service.register(USER_ID, new PushTokenRequest("a".repeat(256))))
                    .isInstanceOf(BusinessException.class)
                    .extracting("errorCode").isEqualTo(PushTokenErrorCode.INVALID_PUSH_TOKEN);

            verifyNoInteractions(repository);
        }
    }

    @Nested
    @DisplayName("delete (삭제)")
    class Delete {

        @Test
        @DisplayName("본인 토큰 삭제를 요청한다")
        void delete() {
            service.delete(USER_ID, new PushTokenRequest("fcm-token"));

            verify(repository).deleteByUserIdAndToken(USER_ID, "fcm-token");
        }

        @ParameterizedTest
        @NullAndEmptySource
        @ValueSource(strings = {"   "})
        @DisplayName("토큰이 비어 있으면 400 INVALID_PUSH_TOKEN이고 삭제하지 않는다")
        void blank(String token) {
            assertThatThrownBy(() -> service.delete(USER_ID, new PushTokenRequest(token)))
                    .isInstanceOf(BusinessException.class)
                    .extracting("errorCode").isEqualTo(PushTokenErrorCode.INVALID_PUSH_TOKEN);

            verifyNoInteractions(repository);
        }

        @Test
        @DisplayName("삭제는 길이를 검사하지 않는다 (명세: REQUIRED만)")
        void noLengthCheck() {
            service.delete(USER_ID, new PushTokenRequest("a".repeat(256)));

            verify(repository).deleteByUserIdAndToken(anyLong(), anyString());
        }
    }
}
