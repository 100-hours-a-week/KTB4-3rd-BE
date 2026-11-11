package com.ktb.moyeota.global.outbox.publisher;

import com.ktb.moyeota.global.event.EventEnvelope;
import com.ktb.moyeota.global.event.EventHeaders;
import com.ktb.moyeota.global.outbox.config.OutboxProperties;
import com.ktb.moyeota.global.outbox.entity.OutboxEvent;
import com.ktb.moyeota.global.outbox.repository.OutboxEventRepository;
import com.ktb.moyeota.global.trace.TraceId;
import io.micrometer.core.instrument.MeterRegistry;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

@Slf4j
@Component
@RequiredArgsConstructor
public class OutboxPublisher {

    static final String ACK_TIMEOUT_ERROR = "ack 대기 시간 초과";
    static final String STUCK_METRIC = "outbox.publish.stuck";

    private static final ZoneId OCCURRED_ZONE = ZoneId.of("Asia/Seoul");
    private static final int LAST_ERROR_MAX_LENGTH = 255;

    private final OutboxEventRepository outboxEventRepository;
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final JsonMapper jsonMapper;
    private final OutboxProperties properties;
    private final Clock clock;
    private final MeterRegistry meterRegistry;

    @Transactional
    public PublishResult publishPending() {
        List<OutboxEvent> events = sendable();
        if (events.isEmpty()) {
            return PublishResult.EMPTY;
        }

        long deadline = System.nanoTime() + properties.ackTimeout().toNanos();
        Map<Long, CompletableFuture<SendResult<String, String>>> sends = new LinkedHashMap<>();
        Map<Long, String> failures = new LinkedHashMap<>();
        Set<String> failedKeys = new HashSet<>();
        for (OutboxEvent event : events) {
            if (System.nanoTime() >= deadline) {
                break;
            }
            String heldKey = heldKey(event);
            if (heldKey != null && failedKeys.contains(heldKey)) {
                continue;
            }
            try {
                sends.put(event.getId(), kafkaTemplate.send(toRecord(event)));
            } catch (RuntimeException e) {
                failures.put(event.getId(), describe(e));
                if (heldKey != null) {
                    failedKeys.add(heldKey);
                }
            }
        }

        List<Long> published = new ArrayList<>();
        for (Map.Entry<Long, CompletableFuture<SendResult<String, String>>> send : sends.entrySet()) {
            try {
                send.getValue().get(Math.max(deadline - System.nanoTime(), 0), TimeUnit.NANOSECONDS);
                published.add(send.getKey());
            } catch (TimeoutException e) {
                failures.put(send.getKey(), ACK_TIMEOUT_ERROR);
            } catch (ExecutionException e) {
                failures.put(send.getKey(), describe(e.getCause()));
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                failures.put(send.getKey(), describe(e));
            }
        }

        if (!published.isEmpty()) {
            outboxEventRepository.markPublished(published, LocalDateTime.now(clock));
        }
        recordFailures(failures);
        alertStuck(events, failures);
        return new PublishResult(published.size(), failures.size());
    }

    private List<OutboxEvent> sendable() {
        Set<String> heldKeys = new HashSet<>();
        List<OutboxEvent> sendable = new ArrayList<>();
        long afterId = 0L;
        int scanned = 0;
        while (sendable.size() < properties.batchSize() && scanned < properties.scanLimit()) {
            int limit = Math.min(properties.batchSize(), properties.scanLimit() - scanned);
            List<OutboxEvent> page = outboxEventRepository.findPending(afterId, limit);
            for (OutboxEvent event : page) {
                if (sendable.size() == properties.batchSize()) {
                    break;
                }
                if (isSendable(event, heldKeys)) {
                    sendable.add(event);
                }
            }
            if (page.size() < limit) {
                break;
            }
            afterId = page.getLast().getId();
            scanned += page.size();
        }
        return sendable;
    }

    private static String heldKey(OutboxEvent event) {
        if (event.getMessageKey() == null) {
            return null;
        }
        return event.getTopic() + "|" + event.getMessageKey();
    }

    private static boolean isSendable(OutboxEvent event, Set<String> heldKeys) {
        String heldKey = heldKey(event);
        if (heldKey == null) {
            return true;
        }
        if (heldKeys.contains(heldKey)) {
            return false;
        }
        if (event.getAttempts() > 0) {
            heldKeys.add(heldKey);
        }
        return true;
    }

    private void alertStuck(List<OutboxEvent> events, Map<Long, String> failures) {
        for (OutboxEvent event : events) {
            String error = failures.get(event.getId());
            if (error != null && event.getAttempts() + 1 == properties.stuckAttempts()) {
                meterRegistry.counter(STUCK_METRIC, "topic", event.getTopic()).increment();
                log.error("[OUTBOX_PUBLISH_STUCK] id={} topic={} key={} attempts={} error={}",
                        event.getId(), event.getTopic(), event.getMessageKey(), properties.stuckAttempts(), error);
            }
        }
    }

    private ProducerRecord<String, String> toRecord(OutboxEvent event) {
        JsonNode data = jsonMapper.readTree(event.getPayload());
        EventEnvelope<JsonNode> envelope = new EventEnvelope<>(
                event.getEventId(),
                event.getEventType(),
                event.getSchemaVersion(),
                event.getOccurredAt().atZone(OCCURRED_ZONE).toOffsetDateTime(),
                data);
        ProducerRecord<String, String> record = new ProducerRecord<>(
                event.getTopic(), event.getMessageKey(), jsonMapper.writeValueAsString(envelope));
        record.headers().add(EventHeaders.EVENT_TYPE, event.getEventType().getBytes(StandardCharsets.UTF_8));
        if (event.getTraceId() != null) {
            record.headers().add(TraceId.KAFKA_HEADER, event.getTraceId().getBytes(StandardCharsets.UTF_8));
        }
        return record;
    }

    private void recordFailures(Map<Long, String> failures) {
        if (failures.isEmpty()) {
            return;
        }
        Map<String, List<Long>> idsByError = new LinkedHashMap<>();
        failures.forEach((id, error) -> idsByError.computeIfAbsent(error, ignored -> new ArrayList<>()).add(id));
        idsByError.forEach((error, ids) -> {
            outboxEventRepository.recordFailure(ids, error);
            log.warn("[OUTBOX_PUBLISH_FAILED] ids={} error={}", ids, error);
        });
    }

    private static String describe(Throwable e) {
        String message = e.getClass().getSimpleName() + ": " + e.getMessage();
        if (message.length() <= LAST_ERROR_MAX_LENGTH) {
            return message;
        }
        return message.substring(0, LAST_ERROR_MAX_LENGTH);
    }

    public record PublishResult(int published, int failed) {

        static final PublishResult EMPTY = new PublishResult(0, 0);
    }
}
