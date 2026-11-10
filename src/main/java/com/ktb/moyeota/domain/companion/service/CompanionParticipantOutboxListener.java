package com.ktb.moyeota.domain.companion.service;

import com.ktb.moyeota.domain.companion.entity.CompanionKind;
import com.ktb.moyeota.domain.companion.event.CompanionParticipantJoinedEvent;
import com.ktb.moyeota.domain.companion.event.CompanionParticipantLeftEvent;
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
public class CompanionParticipantOutboxListener {

    private final OutboxRecorder outboxRecorder;

    @EventListener
    public void onJoined(CompanionParticipantJoinedEvent event) {
        record(event.companionId(), event.kind(), event.userId(), CompanionChangedMessages.PARTICIPANT_JOINED);
    }

    @EventListener
    public void onLeft(CompanionParticipantLeftEvent event) {
        record(event.companionId(), event.kind(), event.userId(), CompanionChangedMessages.PARTICIPANT_LEFT);
    }

    private void record(Long companionId, CompanionKind kind, Long userId, String eventType) {
        outboxRecorder.record(CompanionChangedMessages.TOPIC, CompanionChangedMessages.key(companionId), eventType,
                CompanionChangedMessages.SCHEMA_VERSION, new CompanionChangedData(companionId, kind, userId));
    }
}
