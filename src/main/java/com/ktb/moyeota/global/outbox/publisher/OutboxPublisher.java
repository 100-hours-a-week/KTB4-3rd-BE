package com.ktb.moyeota.global.outbox.publisher;

import com.ktb.moyeota.global.event.EventEnvelope;
import com.ktb.moyeota.global.outbox.config.OutboxProperties;
import com.ktb.moyeota.global.outbox.entity.OutboxEvent;
import com.ktb.moyeota.global.outbox.repository.OutboxEventRepository;
import com.ktb.moyeota.global.trace.TraceId;
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

    private static final ZoneId OCCURRED_ZONE = ZoneId.of("Asia/Seoul");
    private static final int LAST_ERROR_MAX_LENGTH = 255;

    private final OutboxEventRepository outboxEventRepository;
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final JsonMapper jsonMapper;
    private final OutboxProperties properties;
    private final Clock clock;

    @Transactional
    public PublishResult publishPending() {
        List<OutboxEvent> events = sendable(outboxEventRepository.findPending(properties.batchSize()));
        if (events.isEmpty()) {
            return PublishResult.EMPTY;
        }

        long deadline = System.nanoTime() + properties.ackTimeout().toNanos();
        Map<Long, CompletableFuture<SendResult<String, String>>> sends = new LinkedHashMap<>();
        Map<Long, String> failures = new LinkedHashMap<>();
        for (OutboxEvent event : events) {
            if (System.nanoTime() >= deadline) {
                break;
            }
            try {
                sends.put(event.getId(), kafkaTemplate.send(toRecord(event)));
            } catch (RuntimeException e) {
                failures.put(event.getId(), describe(e));
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
        return new PublishResult(published.size(), failures.size());
    }

    private List<OutboxEvent> sendable(List<OutboxEvent> pending) {
        Set<String> heldKeys = new HashSet<>();
        List<OutboxEvent> sendable = new ArrayList<>();
        for (OutboxEvent event : pending) {
            String key = event.getMessageKey();
            if (key == null) {
                sendable.add(event);
                continue;
            }
            String heldKey = event.getTopic() + "|" + key;
            if (heldKeys.contains(heldKey)) {
                continue;
            }
            sendable.add(event);
            if (event.getAttempts() > 0) {
                heldKeys.add(heldKey);
            }
        }
        return sendable;
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
