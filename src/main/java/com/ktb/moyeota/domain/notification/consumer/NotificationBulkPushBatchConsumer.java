package com.ktb.moyeota.domain.notification.consumer;

import com.ktb.moyeota.domain.notification.message.NotificationPushMessages;
import com.ktb.moyeota.domain.notification.message.NotificationPushRequestData;
import com.ktb.moyeota.domain.notification.service.NotificationPushBatchService;
import com.ktb.moyeota.domain.notification.service.PushDeliveryOutcome;
import com.ktb.moyeota.global.config.ConditionalOnServerRole;
import com.ktb.moyeota.global.config.ServerRole;
import com.ktb.moyeota.global.event.EventEnvelope;
import com.ktb.moyeota.global.event.InvalidEventException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.TopicPartition;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaOperations;
import org.springframework.kafka.listener.BatchListenerFailedException;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.retrytopic.RetryTopicConstants;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JavaType;
import tools.jackson.databind.json.JsonMapper;

@Slf4j
@Component
@ConditionalOnServerRole({ServerRole.WORKER, ServerRole.ALL})
@ConditionalOnProperty(prefix = "moyeota.notification.push", name = "mode", havingValue = "kafka")
@ConditionalOnProperty(prefix = NotificationBulkPushConsumerProperties.PREFIX,
        name = NotificationBulkPushConsumerProperties.TYPE, havingValue = "batch")
public class NotificationBulkPushBatchConsumer implements DisposableBean {

    private final NotificationPushBatchService batchService;
    private final DeadLetterPublishingRecoverer deadLetter;
    private final JsonMapper jsonMapper;
    private final JavaType envelopeType;
    private final ExecutorService sendExecutor;

    public NotificationBulkPushBatchConsumer(
            NotificationPushBatchService batchService,
            KafkaOperations<?, ?> kafkaOperations,
            JsonMapper jsonMapper,
            NotificationBulkPushConsumerProperties properties) {
        this.batchService = batchService;
        this.jsonMapper = jsonMapper;
        this.envelopeType = jsonMapper.getTypeFactory()
                .constructParametricType(EventEnvelope.class, NotificationPushRequestData.class);
        this.deadLetter = new DeadLetterPublishingRecoverer(kafkaOperations,
                (record, exception) -> new TopicPartition(record.topic() + RetryTopicConstants.DEFAULT_DLT_SUFFIX, -1));
        int threads = properties.batchSendThreads();
        this.sendExecutor = threads > 0 ? Executors.newFixedThreadPool(threads) : null;
        log.info("[BULK_PUSH_BATCH_STARTED] sendThreads={}", threads);
    }

    @KafkaListener(
            topics = NotificationPushMessages.BULK_PUSH_TOPIC,
            groupId = NotificationBulkPushConsumer.GROUP_ID,
            containerFactory = NotificationBulkPushBatchConsumerConfig.CONTAINER_FACTORY,
            batch = "true",
            properties = "max.poll.records=100")
    public void consume(List<ConsumerRecord<String, String>> records) {
        int invalidIndex = records.size();
        InvalidEventException invalid = null;
        List<Integer> indexes = new ArrayList<>();
        List<Long> jobIds = new ArrayList<>();
        for (int i = 0; i < records.size(); i++) {
            try {
                EventEnvelope<NotificationPushRequestData> envelope = read(records.get(i));
                if (!NotificationPushMessages.BULK_PUSH_REQUESTED.equals(envelope.eventType())) {
                    continue;
                }
                envelope.requireSchemaVersion(NotificationPushMessages.SCHEMA_VERSION);
                indexes.add(i);
                jobIds.add(envelope.data().jobId());
            } catch (InvalidEventException e) {
                invalidIndex = i;
                invalid = e;
                break;
            }
        }

        Map<Long, PushDeliveryOutcome> outcomes =
                batchService.deliverAll(jobIds, NotificationBulkPushConsumerConfig.MAX_ATTEMPTS, sendExecutor);

        for (int k = 0; k < indexes.size(); k++) {
            int index = indexes.get(k);
            long jobId = jobIds.get(k);
            PushDeliveryOutcome outcome = outcomes.get(jobId);
            if (outcome == PushDeliveryOutcome.RETRIES_EXHAUSTED) {
                sendToDeadLetter(records.get(index), new PushRetryException(jobId, outcome));
            } else if (outcome == PushDeliveryOutcome.RETRY_SCHEDULED) {
                throw new BatchListenerFailedException("일시 오류로 다시 받습니다", new PushRetryException(jobId, outcome), index);
            }
        }
        if (invalid != null) {
            throw new BatchListenerFailedException("읽을 수 없는 메시지입니다", invalid, invalidIndex);
        }
    }

    private EventEnvelope<NotificationPushRequestData> read(ConsumerRecord<String, String> record) {
        try {
            return jsonMapper.readValue(record.value(), envelopeType);
        } catch (JacksonException e) {
            if (e.getCause() instanceof InvalidEventException invalid) {
                throw invalid;
            }
            throw new InvalidEventException("읽을 수 없는 메시지입니다. " + e.getOriginalMessage());
        }
    }

    private void sendToDeadLetter(ConsumerRecord<String, String> record, Exception exception) {
        log.error("[BULK_PUSH_DEAD_LETTER] topic={}, partition={}, offset={}",
                record.topic(), record.partition(), record.offset(), exception);
        deadLetter.accept(record, exception);
    }

    @Override
    public void destroy() {
        if (sendExecutor != null) {
            sendExecutor.shutdown();
        }
    }
}
