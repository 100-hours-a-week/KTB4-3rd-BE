package com.ktb.moyeota.domain.notification.service;

import com.ktb.moyeota.domain.notification.message.NotificationPushChannel;
import com.ktb.moyeota.domain.notification.message.NotificationPushMessages;
import com.ktb.moyeota.domain.notification.message.NotificationPushRequestData;
import com.ktb.moyeota.domain.notification.repository.NotificationPushJobRepository;
import com.ktb.moyeota.global.config.NotificationPushConfig;
import com.ktb.moyeota.global.event.EventEnvelopeFactory;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.json.JsonMapper;

@Slf4j
@Component
@ConditionalOnProperty(prefix = "moyeota.notification.push", name = "mode", havingValue = "kafka")
public class NotificationPushPublisher {

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final EventEnvelopeFactory envelopeFactory;
    private final JsonMapper jsonMapper;
    private final NotificationPushJobRepository jobRepository;
    private final Executor ackExecutor;
    private final TransactionTemplate transactionTemplate;
    private final Clock clock;

    public NotificationPushPublisher(KafkaTemplate<String, String> kafkaTemplate,
                                     EventEnvelopeFactory envelopeFactory,
                                     JsonMapper jsonMapper,
                                     NotificationPushJobRepository jobRepository,
                                     @Qualifier(NotificationPushConfig.PUSH_EXECUTOR) Executor ackExecutor,
                                     PlatformTransactionManager transactionManager,
                                     Clock clock) {
        this.kafkaTemplate = kafkaTemplate;
        this.envelopeFactory = envelopeFactory;
        this.jsonMapper = jsonMapper;
        this.jobRepository = jobRepository;
        this.ackExecutor = ackExecutor;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
        this.clock = clock;
    }

    public void publish(NotificationPushChannel channel, List<Long> jobIds) {
        if (jobIds.isEmpty()) {
            return;
        }
        List<CompletableFuture<Long>> sends = jobIds.stream()
                .map(jobId -> send(channel, jobId))
                .toList();
        CompletableFuture.allOf(sends.toArray(CompletableFuture[]::new))
                .handleAsync((ignored, failure) -> {
                    recordPublished(published(sends));
                    return null;
                }, ackExecutor)
                .exceptionally(failure -> {
                    log.warn("[PUSH_PUBLISH_ACK_FAILED] topic={}, jobs={}", channel.topic(), jobIds.size(), failure);
                    return null;
                });
    }

    private CompletableFuture<Long> send(NotificationPushChannel channel, Long jobId) {
        try {
            return kafkaTemplate.send(channel.topic(), toJson(channel, jobId))
                    .thenApply(result -> jobId)
                    .whenComplete((result, failure) -> {
                        if (failure != null) {
                            log.warn("[PUSH_PUBLISH_FAILED] topic={}, jobId={}", channel.topic(), jobId, failure);
                        }
                    });
        } catch (RuntimeException e) {
            log.warn("[PUSH_PUBLISH_FAILED] topic={}, jobId={}", channel.topic(), jobId, e);
            return CompletableFuture.failedFuture(e);
        }
    }

    private String toJson(NotificationPushChannel channel, Long jobId) {
        return jsonMapper.writeValueAsString(envelopeFactory.create(
                channel.eventType(), NotificationPushMessages.SCHEMA_VERSION, new NotificationPushRequestData(jobId)));
    }

    private static List<Long> published(List<CompletableFuture<Long>> sends) {
        return sends.stream()
                .filter(send -> !send.isCompletedExceptionally())
                .map(CompletableFuture::join)
                .toList();
    }

    private void recordPublished(List<Long> jobIds) {
        if (jobIds.isEmpty()) {
            return;
        }
        LocalDateTime now = LocalDateTime.now(clock).truncatedTo(ChronoUnit.MICROS);
        transactionTemplate.executeWithoutResult(status -> jobRepository.markPublished(jobIds, now));
    }
}
