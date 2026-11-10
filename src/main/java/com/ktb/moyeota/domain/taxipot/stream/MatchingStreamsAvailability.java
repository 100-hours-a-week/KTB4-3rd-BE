package com.ktb.moyeota.domain.taxipot.stream;

import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.streams.KafkaStreams;
import org.apache.kafka.streams.KafkaStreams.State;
import org.springframework.boot.availability.AvailabilityChangeEvent;
import org.springframework.boot.availability.LivenessState;
import org.springframework.boot.availability.ReadinessState;
import org.springframework.context.ApplicationEventPublisher;

@Slf4j
public class MatchingStreamsAvailability implements KafkaStreams.StateListener {

    private final ApplicationEventPublisher publisher;

    public MatchingStreamsAvailability(ApplicationEventPublisher publisher) {
        this.publisher = publisher;
    }

    @Override
    public void onChange(State newState, State oldState) {
        if (newState != State.ERROR) {
            return;
        }
        log.error("[KAFKA_STREAMS_MATCHING_ERROR] from={}", oldState);
        AvailabilityChangeEvent.publish(publisher, this, LivenessState.BROKEN);
        AvailabilityChangeEvent.publish(publisher, this, ReadinessState.REFUSING_TRAFFIC);
    }
}
