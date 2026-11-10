package com.ktb.moyeota.domain.settlement.consumer;

import com.ktb.moyeota.domain.settlement.message.ImageAnalysisMessages;
import com.ktb.moyeota.domain.settlement.message.MeterAnalysisResultData;
import com.ktb.moyeota.domain.settlement.message.TransferAnalysisResultData;
import com.ktb.moyeota.domain.settlement.service.MeterAnalysisService;
import com.ktb.moyeota.domain.settlement.service.TransferAnalysisService;
import com.ktb.moyeota.global.config.ConditionalOnServerRole;
import com.ktb.moyeota.global.config.ServerRole;
import com.ktb.moyeota.global.event.EventEnvelope;
import com.ktb.moyeota.global.event.InvalidEventException;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnServerRole({ServerRole.WORKER, ServerRole.ALL})
@ConditionalOnProperty(prefix = "moyeota.settlement.meter-analysis", name = "mode", havingValue = "kafka")
public class ImageAnalysisResultConsumer {

    public static final String GROUP_ID = "settlement-image-analysis-result-applier";

    private final MeterAnalysisService meterAnalysisService;
    private final TransferAnalysisService transferAnalysisService;
    private final JsonMapper jsonMapper;
    private final Clock clock;

    @KafkaListener(topics = ImageAnalysisMessages.RESULT_TOPIC, groupId = GROUP_ID)
    public void consume(@Payload EventEnvelope<JsonNode> envelope) {
        switch (envelope.eventType()) {
            case ImageAnalysisMessages.METER_COMPLETED -> applyMeter(envelope);
            case ImageAnalysisMessages.TRANSFER_COMPLETED -> applyTransfer(envelope);
            default -> log.debug("[IMAGE_ANALYSIS_RESULT_SKIPPED] eventId={}, eventType={}",
                    envelope.eventId(), envelope.eventType());
        }
    }

    private void applyMeter(EventEnvelope<JsonNode> envelope) {
        envelope.requireSchemaVersion(ImageAnalysisMessages.METER_SCHEMA_VERSION);
        MeterAnalysisResultData data = read(envelope, MeterAnalysisResultData.class);
        meterAnalysisService.apply(data.settlementId(), toServerTime(data.analysisStartedAt()), data.toOutcome());
    }

    private void applyTransfer(EventEnvelope<JsonNode> envelope) {
        envelope.requireSchemaVersion(ImageAnalysisMessages.TRANSFER_SCHEMA_VERSION);
        TransferAnalysisResultData data = read(envelope, TransferAnalysisResultData.class);
        transferAnalysisService.apply(data.settlementId(), data.userId(), data.attempt(), data.toOutcome());
    }

    private LocalDateTime toServerTime(OffsetDateTime time) {
        return time.atZoneSameInstant(clock.getZone()).toLocalDateTime();
    }

    private <T> T read(EventEnvelope<JsonNode> envelope, Class<T> type) {
        try {
            return jsonMapper.treeToValue(envelope.data(), type);
        } catch (JacksonException e) {
            if (e.getCause() instanceof InvalidEventException invalid) {
                throw invalid;
            }
            throw new InvalidEventException(
                    "data 를 읽지 못했습니다. event_id=" + envelope.eventId() + " reason=" + e.getOriginalMessage());
        }
    }
}
