package com.ktb.moyeota.domain.notification.dto;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class NotificationReadAllRequestTest {

    @Test
    @DisplayName("max_notification_id가 null이면 처리할 대상이 없고, 값 자체는 유효하다")
    void nullId() {
        NotificationReadAllRequest request = new NotificationReadAllRequest(null);

        assertThat(request.hasTarget()).isFalse();
        assertThat(request.isValidId()).isTrue();
    }

    @ParameterizedTest
    @ValueSource(longs = {1L, 900L, Long.MAX_VALUE})
    @DisplayName("1 이상이면 처리할 대상이 있고 유효하다")
    void validId(long id) {
        NotificationReadAllRequest request = new NotificationReadAllRequest(id);

        assertThat(request.hasTarget()).isTrue();
        assertThat(request.isValidId()).isTrue();
    }

    @ParameterizedTest
    @ValueSource(longs = {0L, -1L, Long.MIN_VALUE})
    @DisplayName("0 이하면 유효하지 않다")
    void invalidId(long id) {
        assertThat(new NotificationReadAllRequest(id).isValidId()).isFalse();
    }
}
