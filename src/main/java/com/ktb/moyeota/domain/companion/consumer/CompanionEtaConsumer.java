package com.ktb.moyeota.domain.companion.consumer;

import com.ktb.moyeota.domain.companion.message.CompanionChangedData;
import com.ktb.moyeota.domain.companion.message.CompanionChangedMessages;
import com.ktb.moyeota.domain.companion.service.CompanionEtaService;
import com.ktb.moyeota.global.config.ConditionalOnServerRole;
import com.ktb.moyeota.global.config.ServerRole;
import com.ktb.moyeota.global.event.EventEnvelope;
import java.time.LocalDateTime;
import java.time.ZoneId;
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
public class CompanionEtaConsumer {

    public static final String GROUP_ID = "companion-eta-estimator";

    private static final ZoneId OCCURRED_ZONE = ZoneId.of("Asia/Seoul");

    private final CompanionEtaService companionEtaService;

    @KafkaListener(topics = CompanionChangedMessages.TOPIC, groupId = GROUP_ID,
            containerFactory = CompanionEtaConsumerConfig.CONTAINER_FACTORY,
            properties = "auto.offset.reset=earliest")
    public void consume(@Payload EventEnvelope<CompanionChangedData> envelope) {
        if (!CompanionChangedMessages.RIDE_STARTED.equals(envelope.eventType())) {
            return;
        }
        envelope.requireSchemaVersion(CompanionChangedMessages.SCHEMA_VERSION);
        LocalDateTime startedAt = envelope.occurredAt().atZoneSameInstant(OCCURRED_ZONE).toLocalDateTime();
        boolean estimated = companionEtaService.estimate(envelope.data().companionId(), startedAt);
        log.info("[COMPANION_ETA_FACT_HANDLED] eventId={}, companionId={}, estimated={}",
                envelope.eventId(), envelope.data().companionId(), estimated);
    }
}
