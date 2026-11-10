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

class CongestionWindowPropertiesTest {

    @Configuration
    @EnableConfigurationProperties(CongestionWindowProperties.class)
    static class TestConfig {
    }

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(TestConfig.class);

    @Test
    @DisplayName("설정하지 않으면 출근 7~10시, 퇴근 17~20시다")
    void defaults() {
        runner.run(context -> {
            CongestionWindowProperties properties = context.getBean(CongestionWindowProperties.class);

            assertThat(properties.morningStartHour()).isEqualTo(7);
            assertThat(properties.morningEndHour()).isEqualTo(10);
            assertThat(properties.eveningStartHour()).isEqualTo(17);
            assertThat(properties.eveningEndHour()).isEqualTo(20);
        });
    }

    @Test
    @DisplayName("출퇴근 시간대를 설정으로 바꿀 수 있다")
    void override() {
        runner.withPropertyValues(
                        "moyeota.congestion.window.morning-start-hour=6",
                        "moyeota.congestion.window.evening-end-hour=21")
                .run(context -> {
                    CongestionWindowProperties properties = context.getBean(CongestionWindowProperties.class);

                    assertThat(properties.morningStartHour()).isEqualTo(6);
                    assertThat(properties.eveningEndHour()).isEqualTo(21);
                });
    }

    @ParameterizedTest(name = "출근 {0}~{1}시, 퇴근 {2}~{3}시는 받지 않는다")
    @CsvSource({
            "10, 7, 17, 20",
            "7, 18, 17, 20",
            "7, 10, 17, 25",
            "-1, 10, 17, 20"
    })
    void rejectsInvalidHours(int morningStart, int morningEnd, int eveningStart, int eveningEnd) {
        assertThatThrownBy(() -> new CongestionWindowProperties(morningStart, morningEnd, eveningStart, eveningEnd))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @ParameterizedTest(name = "{0}시가 출퇴근 시간대인지는 {1}")
    @CsvSource({
            "6, false",
            "7, true",
            "9, true",
            "10, false",
            "17, true",
            "19, true",
            "20, false"
    })
    void commuteHour(int hour, boolean expected) {
        CongestionWindowProperties properties = new CongestionWindowProperties(7, 10, 17, 20);

        assertThat(properties.isCommuteHour(hour)).isEqualTo(expected);
    }
}
