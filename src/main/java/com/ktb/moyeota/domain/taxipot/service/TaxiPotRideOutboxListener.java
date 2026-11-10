package com.ktb.moyeota.domain.taxipot.service;

import com.ktb.moyeota.domain.companion.entity.CompanionKind;
import com.ktb.moyeota.domain.companion.message.CompanionChangedData;
import com.ktb.moyeota.domain.companion.message.CompanionChangedMessages;
import com.ktb.moyeota.domain.taxipot.event.TaxiPotRideEndedEvent;
import com.ktb.moyeota.domain.taxipot.event.TaxiPotRideStartedEvent;
import com.ktb.moyeota.global.outbox.service.OutboxRecorder;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "moyeota.chat.system-message", name = "mode", havingValue = "kafka")
public class TaxiPotRideOutboxListener {

    private final OutboxRecorder outboxRecorder;

    @EventListener
    public void onRideStarted(TaxiPotRideStartedEvent event) {
        record(event.taxiPotId(), CompanionChangedMessages.RIDE_STARTED);
    }

    @EventListener
    public void onRideEnded(TaxiPotRideEndedEvent event) {
        record(event.taxiPotId(), CompanionChangedMessages.RIDE_ENDED);
    }

    private void record(Long taxiPotId, String eventType) {
        outboxRecorder.record(CompanionChangedMessages.TOPIC, CompanionChangedMessages.key(taxiPotId), eventType,
                CompanionChangedMessages.SCHEMA_VERSION,
                new CompanionChangedData(taxiPotId, CompanionKind.TAXI_POT, null));
    }
}
