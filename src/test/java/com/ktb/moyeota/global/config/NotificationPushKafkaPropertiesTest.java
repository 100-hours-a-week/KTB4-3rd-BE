package com.ktb.moyeota.global.config;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class NotificationPushKafkaPropertiesTest {

    private static final Duration REPUBLISH_AFTER = Duration.ofMinutes(2);
    private static final Duration REPUBLISH_PUBLISHED_AFTER = Duration.ofMinutes(10);

    @Test
    @DisplayName("대량 발송 첫 재시도 간격이 0 이하면 기동하지 않는다")
    void rejectsNonPositiveBulkRetryInitialInterval() {
        assertThatThrownBy(() -> new NotificationPushKafkaProperties(
                REPUBLISH_AFTER, REPUBLISH_PUBLISHED_AFTER, 5, 100, Duration.ZERO, 6))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("대량 발송 동시 처리 수가 1 미만이면 기동하지 않는다")
    void rejectsBulkConcurrencyBelowOne() {
        assertThatThrownBy(() -> new NotificationPushKafkaProperties(
                REPUBLISH_AFTER, REPUBLISH_PUBLISHED_AFTER, 5, 100, Duration.ofSeconds(5), 0))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("PUBLISHED 회수 기준이 PENDING 재발행 유예보다 길지 않으면 기동하지 않는다")
    void rejectsPublishedTimeoutNotLongerThanRepublishAfter() {
        assertThatThrownBy(() -> new NotificationPushKafkaProperties(
                REPUBLISH_AFTER, REPUBLISH_AFTER, 5, 100, Duration.ofSeconds(10), 6))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("스윕이 다시 보낼 시도 상한이 1 미만이면 기동하지 않는다")
    void rejectsRepublishMaxAttemptsBelowOne() {
        assertThatThrownBy(() -> new NotificationPushKafkaProperties(
                REPUBLISH_AFTER, REPUBLISH_PUBLISHED_AFTER, 0, 100, Duration.ofSeconds(10), 6))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
