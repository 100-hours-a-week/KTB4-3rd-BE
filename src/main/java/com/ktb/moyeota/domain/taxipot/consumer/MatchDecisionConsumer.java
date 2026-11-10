package com.ktb.moyeota.domain.taxipot.consumer;

import com.ktb.moyeota.domain.taxipot.message.MatchDecidedData;
import com.ktb.moyeota.domain.taxipot.message.MatchingMessages;
import com.ktb.moyeota.domain.taxipot.model.MatchApplyOutcome;
import com.ktb.moyeota.domain.taxipot.service.MatchDecisionService;
import com.ktb.moyeota.global.config.ConditionalOnServerRole;
import com.ktb.moyeota.global.config.ServerRole;
import com.ktb.moyeota.global.event.EventEnvelope;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnServerRole({ServerRole.WORKER, ServerRole.ALL})
@ConditionalOnProperty(prefix = "moyeota.taxi-pot.matching", name = "mode", havingValue = "kafka")
public class MatchDecisionConsumer {

    public static final String GROUP_ID = "matching-decision-reflector";

    private final MatchDecisionService matchDecisionService;

    @KafkaListener(
            topics = MatchingMessages.DECISION_TOPIC,
            groupId = GROUP_ID,
            containerFactory = MatchDecisionConsumerConfig.CONTAINER_FACTORY,
            properties = "isolation.level=read_committed")
    public void consume(
            @Payload EventEnvelope<MatchDecidedData> envelope,
            @Header(KafkaHeaders.RECEIVED_KEY) String cellKey) {
        if (!MatchingMessages.DECIDED.equals(envelope.eventType())) {
            log.warn("[MATCH_DECISION_UNKNOWN_EVENT] eventId={}, eventType={}",
                    envelope.eventId(), envelope.eventType());
            return;
        }
        envelope.requireSchemaVersion(MatchingMessages.SCHEMA_VERSION);
        MatchApplyOutcome outcome = matchDecisionService.apply(cellKey, envelope.data());
        log.info("[MATCH_DECISION_APPLIED] eventId={}, cell={}, resultId={}, outcome={}",
                envelope.eventId(), cellKey, envelope.data().resultId(), outcome);
    }
}
