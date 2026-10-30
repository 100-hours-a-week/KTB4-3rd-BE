package com.ktb.moyeota.domain.taxipot.stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.ktb.moyeota.global.config.ClockConfig;
import com.ktb.moyeota.global.config.KafkaTestContainerConfig;
import com.ktb.moyeota.global.event.EventEnvelopeFactory;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Duration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.autoconfigure.availability.ApplicationAvailabilityAutoConfiguration;
import org.springframework.boot.availability.ApplicationAvailability;
import org.springframework.boot.availability.LivenessState;
import org.springframework.boot.availability.ReadinessState;
import org.springframework.boot.jackson.autoconfigure.JacksonAutoConfiguration;
import org.springframework.boot.kafka.autoconfigure.KafkaAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnectionAutoConfiguration;
import org.springframework.context.annotation.Bean;

@SpringBootTest(
        classes = {KafkaTestContainerConfig.class, ClockConfig.class, EventEnvelopeFactory.class,
                MatchingStreamsConfig.class, MatchingStreamsErrorAvailabilityTest.MetricsConfig.class},
        properties = {
                "APP_ROLE=worker",
                "moyeota.taxi-pot.matching.mode=kafka",
                "spring.kafka.streams.application-id=moyeota-matching-availability-test"})
@ImportAutoConfiguration({
        JacksonAutoConfiguration.class,
        KafkaAutoConfiguration.class,
        ServiceConnectionAutoConfiguration.class,
        ApplicationAvailabilityAutoConfiguration.class})
class MatchingStreamsErrorAvailabilityTest {

    @Autowired
    private ApplicationAvailability availability;

    @Test
    @DisplayName("입력 토픽이 없어 Streams 가 ERROR 로 멈추면 liveness 와 readiness 가 내려간다")
    void breaksWhenSourceTopicIsMissing() {
        await().atMost(Duration.ofSeconds(60)).untilAsserted(() -> {
            assertThat(availability.getLivenessState()).isEqualTo(LivenessState.BROKEN);
            assertThat(availability.getReadinessState()).isEqualTo(ReadinessState.REFUSING_TRAFFIC);
        });
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class MetricsConfig {

        @Bean
        MeterRegistry meterRegistry() {
            return new SimpleMeterRegistry();
        }
    }
}
