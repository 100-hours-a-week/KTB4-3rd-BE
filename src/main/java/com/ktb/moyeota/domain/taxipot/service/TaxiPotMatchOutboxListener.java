package com.ktb.moyeota.domain.taxipot.service;

import com.ktb.moyeota.domain.companion.entity.CompanionKind;
import com.ktb.moyeota.domain.companion.message.CompanionChangedData;
import com.ktb.moyeota.domain.companion.message.CompanionChangedMessages;
import com.ktb.moyeota.domain.taxipot.event.TaxiPotMatchedEvent;
import com.ktb.moyeota.global.outbox.service.OutboxRecorder;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "moyeota.chat.system-message", name = "mode", havingValue = "kafka")
public class TaxiPotMatchOutboxListener {

    private final OutboxRecorder outboxRecorder;

    @EventListener
    public void onMatched(TaxiPotMatchedEvent event) {
        event.matchedUserIds().forEach(userId -> outboxRecorder.record(CompanionChangedMessages.TOPIC,
                CompanionChangedMessages.key(event.taxiPotId()), CompanionChangedMessages.MATCHED,
                CompanionChangedMessages.SCHEMA_VERSION,
                new CompanionChangedData(event.taxiPotId(), CompanionKind.TAXI_POT, userId)));
    }
}
