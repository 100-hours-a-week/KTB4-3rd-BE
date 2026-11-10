package com.ktb.moyeota.domain.chat.consumer;

import com.ktb.moyeota.domain.chat.service.ChatCompanionFactWriter;
import com.ktb.moyeota.domain.companion.message.CompanionChangedData;
import com.ktb.moyeota.domain.companion.message.CompanionChangedMessages;
import com.ktb.moyeota.global.config.ConditionalOnServerRole;
import com.ktb.moyeota.global.config.ServerRole;
import com.ktb.moyeota.global.event.EventEnvelope;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnServerRole({ServerRole.WORKER, ServerRole.ALL})
@ConditionalOnProperty(prefix = "moyeota.chat.system-message", name = "mode", havingValue = "kafka")
public class ChatCompanionFactConsumer {

    public static final String GROUP_ID = "chat-system-message-writer";

    private static final Set<String> SUPPORTED_EVENTS = Set.of(
            CompanionChangedMessages.MATCHED,
            CompanionChangedMessages.PARTICIPANT_JOINED,
            CompanionChangedMessages.PARTICIPANT_LEFT,
            CompanionChangedMessages.RIDE_STARTED,
            CompanionChangedMessages.RIDE_ENDED);

    private final ChatCompanionFactWriter writer;

    @KafkaListener(topics = CompanionChangedMessages.TOPIC, groupId = GROUP_ID)
    public void consume(@Payload EventEnvelope<CompanionChangedData> envelope) {
        if (!SUPPORTED_EVENTS.contains(envelope.eventType())) {
            log.debug("[CHAT_COMPANION_FACT_SKIPPED] eventId={}, eventType={}",
                    envelope.eventId(), envelope.eventType());
            return;
        }
        envelope.requireSchemaVersion(CompanionChangedMessages.SCHEMA_VERSION);
        boolean written = writer.write(envelope.eventId(), envelope.eventType(), envelope.data());
        log.info("[CHAT_COMPANION_FACT_WRITTEN] eventId={}, eventType={}, companionId={}, written={}",
                envelope.eventId(), envelope.eventType(), envelope.data().companionId(), written);
    }
}
