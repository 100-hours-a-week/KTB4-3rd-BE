package com.ktb.moyeota.domain.user.dto;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class PushTokenRequestTest {

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "   ", "\t"})
    @DisplayName("null, 빈 값, 공백만 있으면 비어 있는 토큰이다")
    void blank(String token) {
        assertThat(new PushTokenRequest(token).isBlank()).isTrue();
    }

    @Test
    @DisplayName("값이 있으면 비어 있지 않다")
    void notBlank() {
        assertThat(new PushTokenRequest("fcm-token").isBlank()).isFalse();
    }

    @Test
    @DisplayName("255자까지는 허용하고 256자부터 초과다")
    void maxLength() {
        assertThat(new PushTokenRequest("a".repeat(255)).exceedsMaxLength()).isFalse();
        assertThat(new PushTokenRequest("a".repeat(256)).exceedsMaxLength()).isTrue();
        assertThat(new PushTokenRequest(null).exceedsMaxLength()).isFalse();
    }
}
