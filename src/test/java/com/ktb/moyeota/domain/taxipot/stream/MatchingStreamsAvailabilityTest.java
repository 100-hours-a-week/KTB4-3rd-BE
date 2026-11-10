package com.ktb.moyeota.domain.taxipot.stream;

import static org.assertj.core.api.Assertions.assertThat;

import org.apache.kafka.streams.KafkaStreams.State;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.availability.ApplicationAvailabilityAutoConfiguration;
import org.springframework.boot.availability.ApplicationAvailability;
import org.springframework.boot.availability.AvailabilityChangeEvent;
import org.springframework.boot.availability.LivenessState;
import org.springframework.boot.availability.ReadinessState;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class MatchingStreamsAvailabilityTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(ApplicationAvailabilityAutoConfiguration.class));

    @Test
    @DisplayName("Streams 가 ERROR 가 되면 liveness 는 BROKEN, readiness 는 REFUSING_TRAFFIC 이 된다")
    void breaksOnError() {
        runner.run(context -> {
            AvailabilityChangeEvent.publish(context, LivenessState.CORRECT);
            AvailabilityChangeEvent.publish(context, ReadinessState.ACCEPTING_TRAFFIC);

            new MatchingStreamsAvailability(context).onChange(State.ERROR, State.PENDING_ERROR);

            ApplicationAvailability availability = context.getBean(ApplicationAvailability.class);
            assertThat(availability.getLivenessState()).isEqualTo(LivenessState.BROKEN);
            assertThat(availability.getReadinessState()).isEqualTo(ReadinessState.REFUSING_TRAFFIC);
        });
    }

    @Test
    @DisplayName("리밸런스나 정상 종료는 가용성을 바꾸지 않는다")
    void keepsAvailabilityOtherwise() {
        runner.run(context -> {
            AvailabilityChangeEvent.publish(context, LivenessState.CORRECT);
            AvailabilityChangeEvent.publish(context, ReadinessState.ACCEPTING_TRAFFIC);
            MatchingStreamsAvailability listener = new MatchingStreamsAvailability(context);

            listener.onChange(State.REBALANCING, State.RUNNING);
            listener.onChange(State.PENDING_SHUTDOWN, State.RUNNING);
            listener.onChange(State.NOT_RUNNING, State.PENDING_SHUTDOWN);

            ApplicationAvailability availability = context.getBean(ApplicationAvailability.class);
            assertThat(availability.getLivenessState()).isEqualTo(LivenessState.CORRECT);
            assertThat(availability.getReadinessState()).isEqualTo(ReadinessState.ACCEPTING_TRAFFIC);
        });
    }
}
