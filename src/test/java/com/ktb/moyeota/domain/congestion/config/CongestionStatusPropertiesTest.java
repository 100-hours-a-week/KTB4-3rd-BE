package com.ktb.moyeota.domain.congestion.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

class CongestionStatusPropertiesTest {

    @Configuration
    @EnableConfigurationProperties(CongestionStatusProperties.class)
    static class TestConfig {
    }

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(TestConfig.class);

    @Test
    @DisplayName("설정하지 않으면 사고 · 지연 기준은 모두 3명이다")
    void defaults() {
        runner.run(context -> {
            CongestionStatusProperties properties = context.getBean(CongestionStatusProperties.class);

            assertThat(properties.accidentReporterThreshold()).isEqualTo(3);
            assertThat(properties.delayReporterThreshold()).isEqualTo(3);
        });
    }

    @Test
    @DisplayName("사고 · 지연 기준을 설정으로 바꿀 수 있다")
    void override() {
        runner.withPropertyValues(
                        "moyeota.congestion.status.accident-reporter-threshold=4",
                        "moyeota.congestion.status.delay-reporter-threshold=2")
                .run(context -> {
                    CongestionStatusProperties properties = context.getBean(CongestionStatusProperties.class);

                    assertThat(properties.accidentReporterThreshold()).isEqualTo(4);
                    assertThat(properties.delayReporterThreshold()).isEqualTo(2);
                });
    }

    @ParameterizedTest(name = "사고 {0}명, 지연 {1}명 기준은 받지 않는다")
    @CsvSource({"0, 3", "3, 0"})
    void rejectsZero(int accident, int delay) {
        assertThatThrownBy(() -> new CongestionStatusProperties(accident, delay))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
