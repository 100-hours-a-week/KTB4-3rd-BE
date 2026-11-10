package com.ktb.moyeota.domain.congestion.consumer;

import com.ktb.moyeota.domain.congestion.message.CongestionAnalysisResultData;
import com.ktb.moyeota.domain.congestion.message.CongestionMessages;
import com.ktb.moyeota.domain.congestion.service.CongestionResultService;
import com.ktb.moyeota.domain.congestion.service.ResultApplyOutcome;
import com.ktb.moyeota.global.config.ConditionalOnServerRole;
import com.ktb.moyeota.global.config.ServerRole;
import com.ktb.moyeota.global.event.EventEnvelope;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnServerRole({ServerRole.WORKER, ServerRole.ALL})
public class CongestionResultConsumer {

    public static final String GROUP_ID = "congestion-result-applier";

    private final CongestionResultService resultService;

    @KafkaListener(topics = CongestionMessages.RESULT_TOPIC, groupId = GROUP_ID)
    public void consume(@Payload EventEnvelope<CongestionAnalysisResultData> envelope) {
        if (!CongestionMessages.ANALYSIS_COMPLETED.equals(envelope.eventType())) {
            log.debug("[CONGESTION_RESULT_SKIPPED] eventId={}, eventType={}", envelope.eventId(), envelope.eventType());
            return;
        }
        envelope.requireSchemaVersion(CongestionMessages.SCHEMA_VERSION);
        CongestionAnalysisResultData data = envelope.data();
        ResultApplyOutcome outcome = resultService.apply(data);
        log.info("[CONGESTION_RESULT_APPLIED] eventId={}, spotId={}, windowEnd={}, outcome={}",
                envelope.eventId(), data.spotId(), data.windowEnd(), outcome);
    }
}
