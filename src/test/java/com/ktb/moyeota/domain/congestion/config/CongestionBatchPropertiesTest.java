package com.ktb.moyeota.domain.congestion.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

class CongestionBatchPropertiesTest {

    @Configuration
    @EnableConfigurationProperties(CongestionBatchProperties.class)
    static class TestConfig {
    }

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(TestConfig.class);

    @Test
    @DisplayName("설정하지 않으면 유예는 30초다")
    void defaults() {
        runner.run(context -> assertThat(context.getBean(CongestionBatchProperties.class).grace())
                .isEqualTo(Duration.ofSeconds(30)));
    }

    @Test
    @DisplayName("유예를 설정으로 바꿀 수 있다")
    void override() {
        runner.withPropertyValues("moyeota.congestion.batch.grace=45s")
                .run(context -> assertThat(context.getBean(CongestionBatchProperties.class).grace())
                        .isEqualTo(Duration.ofSeconds(45)));
    }

    @Test
    @DisplayName("유예는 음수일 수 없다")
    void rejectsNegative() {
        assertThatThrownBy(() -> new CongestionBatchProperties(Duration.ofSeconds(-1)))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
