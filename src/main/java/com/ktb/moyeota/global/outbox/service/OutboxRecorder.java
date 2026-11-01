package com.ktb.moyeota.global.outbox.service;

import com.ktb.moyeota.global.event.EventEnvelope;
import com.ktb.moyeota.global.event.EventEnvelopeFactory;
import com.ktb.moyeota.global.outbox.entity.OutboxEvent;
import com.ktb.moyeota.global.outbox.repository.OutboxEventRepository;
import com.ktb.moyeota.global.trace.TraceId;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;
import lombok.RequiredArgsConstructor;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

@Component
@RequiredArgsConstructor
public class OutboxRecorder {

    private static final ZoneId OCCURRED_ZONE = ZoneId.of("Asia/Seoul");
    private static final int TRACE_ID_MAX_LENGTH = 64;

    private final OutboxEventRepository outboxEventRepository;
    private final EventEnvelopeFactory envelopeFactory;
    private final JsonMapper jsonMapper;
    private final Clock clock;

    @Transactional(propagation = Propagation.MANDATORY)
    public OutboxEvent record(String topic, String messageKey, String eventType, int schemaVersion, Object data) {
        EventEnvelope<Object> envelope = envelopeFactory.create(eventType, schemaVersion, data);
        return outboxEventRepository.save(OutboxEvent.create(
                topic,
                messageKey,
                envelope.eventId(),
                envelope.eventType(),
                envelope.schemaVersion(),
                envelope.occurredAt().atZoneSameInstant(OCCURRED_ZONE).toLocalDateTime(),
                jsonMapper.writeValueAsString(data),
                traceId(),
                LocalDateTime.now(clock)));
    }

    private static String traceId() {
        String traceId = MDC.get(TraceId.LOG_KEY);
        if (traceId == null || traceId.length() <= TRACE_ID_MAX_LENGTH) {
            return traceId;
        }
        return traceId.substring(0, TRACE_ID_MAX_LENGTH);
    }
}
