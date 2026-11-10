package com.ktb.moyeota.domain.chat.consumer;

import com.ktb.moyeota.domain.chat.service.ChatSettlementFactWriter;
import com.ktb.moyeota.domain.settlement.message.SettlementChangedData;
import com.ktb.moyeota.domain.settlement.message.SettlementChangedMessages;
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
public class ChatSettlementFactConsumer {

    private static final Set<String> SUPPORTED_EVENTS = Set.of(
            SettlementChangedMessages.TRANSFER_REQUESTED,
            SettlementChangedMessages.TRANSFER_EXHAUSTED,
            SettlementChangedMessages.COMPLETED);

    private final ChatSettlementFactWriter writer;

    @KafkaListener(topics = SettlementChangedMessages.TOPIC, groupId = ChatCompanionFactConsumer.GROUP_ID)
    public void consume(@Payload EventEnvelope<SettlementChangedData> envelope) {
        if (!SUPPORTED_EVENTS.contains(envelope.eventType())) {
            log.debug("[CHAT_SETTLEMENT_FACT_SKIPPED] eventId={}, eventType={}",
                    envelope.eventId(), envelope.eventType());
            return;
        }
        envelope.requireSchemaVersion(SettlementChangedMessages.SCHEMA_VERSION);
        boolean written = writer.write(envelope.eventId(), envelope.eventType(), envelope.data());
        log.info("[CHAT_SETTLEMENT_FACT_WRITTEN] eventId={}, eventType={}, settlementId={}, written={}",
                envelope.eventId(), envelope.eventType(), envelope.data().settlementId(), written);
    }
}
