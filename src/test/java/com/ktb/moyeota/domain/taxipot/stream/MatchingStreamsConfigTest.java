package com.ktb.moyeota.domain.taxipot.stream;

import static org.assertj.core.api.Assertions.assertThat;

import com.ktb.moyeota.global.event.EventEnvelopeFactory;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Clock;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.jackson.autoconfigure.JacksonAutoConfiguration;
import org.springframework.boot.kafka.autoconfigure.KafkaAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.kafka.config.StreamsBuilderFactoryBean;

class MatchingStreamsConfigTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(JacksonAutoConfiguration.class, KafkaAutoConfiguration.class))
            .withBean(Clock.class, Clock::systemDefaultZone)
            .withBean(MeterRegistry.class, SimpleMeterRegistry::new)
            .withUserConfiguration(EventEnvelopeFactory.class, MatchingStreamsConfig.class)
            .withPropertyValues(
                    "spring.kafka.streams.application-id=moyeota-matching", "spring.kafka.streams.auto-startup=false");

    @Test
    @DisplayName("worker 와 all 역할에서 kafka 매칭 방식일 때 Streams 를 켠다")
    void enabledOnWorkerWithKafkaMode() {
        runner.withPropertyValues("APP_ROLE=worker", "moyeota.taxi-pot.matching.mode=kafka")
                .run(context -> assertThat(context).hasSingleBean(StreamsBuilderFactoryBean.class));
        runner.withPropertyValues("APP_ROLE=all", "moyeota.taxi-pot.matching.mode=kafka")
                .run(context -> assertThat(context).hasSingleBean(StreamsBuilderFactoryBean.class));
    }

    @Test
    @DisplayName("rest 역할이거나 매칭 방식이 kafka 가 아니면 Streams 를 켜지 않는다")
    void disabledOtherwise() {
        runner.withPropertyValues("APP_ROLE=rest", "moyeota.taxi-pot.matching.mode=kafka")
                .run(context -> assertThat(context).doesNotHaveBean(StreamsBuilderFactoryBean.class));
        runner.withPropertyValues("APP_ROLE=worker", "moyeota.taxi-pot.matching.mode=batch")
                .run(context -> assertThat(context).doesNotHaveBean(StreamsBuilderFactoryBean.class));
        runner.withPropertyValues("APP_ROLE=worker")
                .run(context -> assertThat(context).doesNotHaveBean(StreamsBuilderFactoryBean.class));
    }
}
