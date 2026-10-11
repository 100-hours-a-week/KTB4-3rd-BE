package com.ktb.moyeota.domain.notification.push;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class PushSendResultTest {

    @Test
    @DisplayName("토큰 중 하나라도 성공하면 anySuccess가 true다")
    void anySuccess() {
        PushSendResult result = new PushSendResult(List.of(
                TokenSendResult.success("phone"),
                TokenSendResult.failure("pc", TokenSendOutcome.TRANSIENT_FAILURE, "UNAVAILABLE")));

        assertThat(result.anySuccess()).isTrue();
        assertThat(result.hasTransientFailure()).isTrue();
    }

    @Test
    @DisplayName("성공이 없고 일시 오류도 없으면 둘 다 false다")
    void noSuccessNoTransient() {
        PushSendResult result = new PushSendResult(List.of(
                TokenSendResult.failure("a", TokenSendOutcome.UNREGISTERED, "UNREGISTERED"),
                TokenSendResult.failure("b", TokenSendOutcome.PERMANENT_FAILURE, "INVALID_ARGUMENT")));

        assertThat(result.anySuccess()).isFalse();
        assertThat(result.hasTransientFailure()).isFalse();
    }

    @Test
    @DisplayName("UNREGISTERED 토큰만 골라낸다")
    void unregisteredTokens() {
        PushSendResult result = new PushSendResult(List.of(
                TokenSendResult.success("ok"),
                TokenSendResult.failure("gone1", TokenSendOutcome.UNREGISTERED, "UNREGISTERED"),
                TokenSendResult.failure("bad", TokenSendOutcome.PERMANENT_FAILURE, "INVALID_ARGUMENT"),
                TokenSendResult.failure("gone2", TokenSendOutcome.UNREGISTERED, "UNREGISTERED")));

        assertThat(result.unregisteredTokens()).containsExactly("gone1", "gone2");
    }

    @Test
    @DisplayName("오류 코드를 중복 없이 쉼표로 묶고, 실패가 없으면 null이다")
    void errorSummary() {
        PushSendResult failed = new PushSendResult(List.of(
                TokenSendResult.failure("a", TokenSendOutcome.TRANSIENT_FAILURE, "UNAVAILABLE"),
                TokenSendResult.failure("b", TokenSendOutcome.TRANSIENT_FAILURE, "UNAVAILABLE"),
                TokenSendResult.failure("c", TokenSendOutcome.UNREGISTERED, "UNREGISTERED")));
        PushSendResult succeeded = new PushSendResult(List.of(TokenSendResult.success("a")));

        assertThat(failed.errorSummary()).isEqualTo("UNAVAILABLE,UNREGISTERED");
        assertThat(succeeded.errorSummary()).isNull();
    }

    @Test
    @DisplayName("토큰이 없으면 빈 결과다")
    void empty() {
        PushSendResult result = new PushSendResult(null);

        assertThat(result.results()).isEmpty();
        assertThat(result.anySuccess()).isFalse();
        assertThat(result.errorSummary()).isNull();
    }
}
