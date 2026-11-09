package com.ktb.moyeota.domain.notification.consumer;

import static org.assertj.core.api.Assertions.assertThat;

import com.ktb.moyeota.global.config.NotificationPushKafkaProperties;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.RepeatedTest;
import org.springframework.util.backoff.BackOffExecution;

class NotificationBulkPushConsumerConfigTest {

    private static final NotificationPushKafkaProperties DEFAULTS =
            new NotificationPushKafkaProperties(Duration.ofMinutes(2), 100, Duration.ofSeconds(10), 6);

    @RepeatedTest(20)
    @DisplayName("재시도 대기는 10초에서 두 배씩 늘어 4번(약 10·20·40·80초) 하고 멈추며, 매번 ±20% 안에서 흔들린다")
    void backOffDoublesFourTimesWithJitter() {
        BackOffExecution execution = NotificationBulkPushConsumerConfig.backOff(DEFAULTS).start();

        List<Long> waits = new ArrayList<>();
        long next;
        while ((next = execution.nextBackOff()) != BackOffExecution.STOP) {
            waits.add(next);
        }

        assertThat(waits).hasSize(NotificationBulkPushConsumerConfig.MAX_RETRIES);
        long[] expected = {10_000, 20_000, 40_000, 80_000};
        for (int i = 0; i < expected.length; i++) {
            assertThat(waits.get(i)).isBetween(expected[i] * 8 / 10, expected[i] * 12 / 10);
        }
    }
}
