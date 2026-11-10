package com.ktb.moyeota.domain.carpool.service;

import com.ktb.moyeota.domain.carpool.event.CarpoolRideEndedEvent;
import com.ktb.moyeota.domain.carpool.event.CarpoolRideStartedEvent;
import com.ktb.moyeota.domain.companion.entity.CompanionKind;
import com.ktb.moyeota.domain.companion.message.CompanionChangedData;
import com.ktb.moyeota.domain.companion.message.CompanionChangedMessages;
import com.ktb.moyeota.global.outbox.service.OutboxRecorder;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "moyeota.chat.system-message", name = "mode", havingValue = "kafka")
public class CarpoolRideOutboxListener {

    private final OutboxRecorder outboxRecorder;

    @EventListener
    public void onRideStarted(CarpoolRideStartedEvent event) {
        record(event.carpoolId(), CompanionChangedMessages.RIDE_STARTED);
    }

    @EventListener
    public void onRideEnded(CarpoolRideEndedEvent event) {
        record(event.carpoolId(), CompanionChangedMessages.RIDE_ENDED);
    }

    private void record(Long carpoolId, String eventType) {
        outboxRecorder.record(CompanionChangedMessages.TOPIC, CompanionChangedMessages.key(carpoolId), eventType,
                CompanionChangedMessages.SCHEMA_VERSION,
                new CompanionChangedData(carpoolId, CompanionKind.CARPOOL, null));
    }
}
