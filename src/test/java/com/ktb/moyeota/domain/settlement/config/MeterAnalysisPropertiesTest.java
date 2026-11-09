package com.ktb.moyeota.domain.settlement.config;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class MeterAnalysisPropertiesTest {

    @Test
    @DisplayName("만료 시간이 대기 상한보다 길지 않으면 기동하지 않는다")
    void rejectsExpiryNotLongerThanQueueWait() {
        assertThatThrownBy(() -> new MeterAnalysisProperties(2, 20, Duration.ofSeconds(90), Duration.ofSeconds(90)))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
