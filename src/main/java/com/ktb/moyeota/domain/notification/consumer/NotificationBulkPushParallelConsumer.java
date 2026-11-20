package com.ktb.moyeota.domain.notification.consumer;

import com.ktb.moyeota.domain.notification.message.NotificationPushMessages;
import com.ktb.moyeota.domain.notification.message.NotificationPushRequestData;
import com.ktb.moyeota.domain.notification.service.NotificationPushBatchService;
import com.ktb.moyeota.domain.notification.service.NotificationPushService;
import com.ktb.moyeota.domain.notification.service.PushDeliveryOutcome;
import com.ktb.moyeota.global.config.ConditionalOnServerRole;
import com.ktb.moyeota.global.config.NotificationPushKafkaProperties;
import com.ktb.moyeota.global.config.ServerRole;
import com.ktb.moyeota.global.event.EventEnvelope;
import com.ktb.moyeota.global.event.InvalidEventException;
import io.confluent.parallelconsumer.ParallelConsumerOptions;
import io.confluent.parallelconsumer.ParallelConsumerOptions.ProcessingOrder;
import io.confluent.parallelconsumer.ParallelStreamProcessor;
import io.confluent.parallelconsumer.PollContext;
import io.confluent.parallelconsumer.RecordContext;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.TopicPartition;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.SmartLifecycle;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.KafkaOperations;
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
        name = NotificationBulkPushConsumerProperties.TYPE, havingValue = "parallel")
public class NotificationBulkPushParallelConsumer implements SmartLifecycle {

    private static final String CLIENT_ID_PREFIX = "notification-bulk-push-parallel";
    private static final Duration DRAIN_TIMEOUT = Duration.ofSeconds(30);

    private final ConsumerFactory<Object, Object> consumerFactory;
    private final NotificationPushService pushService;
    private final NotificationPushBatchService batchService;
    private final NotificationPushKafkaProperties kafkaProperties;
    private final NotificationBulkPushParallelProperties parallelProperties;
    private final DeadLetterPublishingRecoverer deadLetter;
    private final JsonMapper jsonMapper;
    private final JavaType envelopeType;
    private final ObjectProvider<MeterRegistry> meterRegistry;

    private volatile ParallelStreamProcessor<Object, Object> processor;

    public NotificationBulkPushParallelConsumer(
            ConsumerFactory<Object, Object> consumerFactory,
            KafkaOperations<?, ?> kafkaOperations,
            NotificationPushService pushService,
            NotificationPushBatchService batchService,
            NotificationPushKafkaProperties kafkaProperties,
            NotificationBulkPushParallelProperties parallelProperties,
            JsonMapper jsonMapper,
            ObjectProvider<MeterRegistry> meterRegistry) {
        this.consumerFactory = consumerFactory;
        this.pushService = pushService;
        this.batchService = batchService;
        this.kafkaProperties = kafkaProperties;
        this.parallelProperties = parallelProperties;
        this.jsonMapper = jsonMapper;
        this.meterRegistry = meterRegistry;
        this.envelopeType = jsonMapper.getTypeFactory()
                .constructParametricType(EventEnvelope.class, NotificationPushRequestData.class);
        this.deadLetter = new DeadLetterPublishingRecoverer(kafkaOperations,
                (record, exception) -> new TopicPartition(record.topic() + RetryTopicConstants.DEFAULT_DLT_SUFFIX, -1));
    }

    @Override
    public void start() {
        Properties overrides = new Properties();
        overrides.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, "false");
        Consumer<Object, Object> consumer = consumerFactory.createConsumer(
                NotificationBulkPushConsumer.GROUP_ID, CLIENT_ID_PREFIX, null, overrides);
        ParallelConsumerOptions.ParallelConsumerOptionsBuilder<Object, Object> options =
                ParallelConsumerOptions.<Object, Object>builder()
                        .consumer(consumer)
                        .ordering(ProcessingOrder.UNORDERED)
                        .maxConcurrency(parallelProperties.concurrency())
                        .retryDelayProvider(this::retryDelay);
        boolean batched = parallelProperties.batchSize() > 1;
        if (batched) {
            options.batchSize(parallelProperties.batchSize());
        }
        meterRegistry.ifAvailable(options::meterRegistry);
        ParallelStreamProcessor<Object, Object> created =
                ParallelStreamProcessor.createEosStreamProcessor(options.build());
        created.subscribe(List.of(NotificationPushMessages.BULK_PUSH_TOPIC));
        created.poll(batched ? this::consumeBatch : this::consume);
        processor = created;
        log.info("[BULK_PUSH_PARALLEL_STARTED] concurrency={}, batchSize={}",
                parallelProperties.concurrency(), parallelProperties.batchSize());
    }

    @Override
    public void stop() {
        ParallelStreamProcessor<Object, Object> current = processor;
        processor = null;
        if (current != null) {
            current.closeDrainFirst(DRAIN_TIMEOUT);
        }
    }

    @Override
    public boolean isRunning() {
        return processor != null;
    }

    void consume(PollContext<Object, Object> context) {
        ConsumerRecord<Object, Object> record = context.getSingleConsumerRecord();
        int attempt = context.getSingleRecord().getNumberOfFailedAttempts() + 1;
        try {
            deliver(read(record), record);
        } catch (InvalidEventException e) {
            sendToDeadLetter(record, e);
        } catch (PushRetryException e) {
            throw e;
        } catch (RuntimeException e) {
            if (attempt < NotificationBulkPushConsumerConfig.MAX_ATTEMPTS) {
                throw e;
            }
            sendToDeadLetter(record, e);
        }
    }

    void consumeBatch(PollContext<Object, Object> context) {
        int attempt = context.stream().mapToInt(RecordContext::getNumberOfFailedAttempts).max().orElse(0) + 1;
        List<ConsumerRecord<Object, Object>> records = new ArrayList<>();
        List<Long> jobIds = new ArrayList<>();
        for (ConsumerRecord<Object, Object> record : context.getConsumerRecordsFlattened()) {
            try {
                EventEnvelope<NotificationPushRequestData> envelope = read(record);
                if (!NotificationPushMessages.BULK_PUSH_REQUESTED.equals(envelope.eventType())) {
                    log.warn("[BULK_PUSH_REQUEST_UNKNOWN_EVENT] eventId={}, eventType={}",
                            envelope.eventId(), envelope.eventType());
                    continue;
                }
                envelope.requireSchemaVersion(NotificationPushMessages.SCHEMA_VERSION);
                records.add(record);
                jobIds.add(envelope.data().jobId());
            } catch (InvalidEventException e) {
                sendToDeadLetter(record, e);
            }
        }
        Map<Long, PushDeliveryOutcome> outcomes;
        try {
            outcomes = batchService.deliverAll(jobIds, NotificationBulkPushConsumerConfig.MAX_ATTEMPTS, null);
        } catch (RuntimeException e) {
            if (attempt < NotificationBulkPushConsumerConfig.MAX_ATTEMPTS) {
                throw e;
            }
            records.forEach(record -> sendToDeadLetter(record, e));
            return;
        }
        PushRetryException retry = null;
        for (int i = 0; i < records.size(); i++) {
            long jobId = jobIds.get(i);
            PushDeliveryOutcome outcome = outcomes.get(jobId);
            if (outcome == PushDeliveryOutcome.RETRIES_EXHAUSTED) {
                sendToDeadLetter(records.get(i), new PushRetryException(jobId, outcome));
            } else if (outcome == PushDeliveryOutcome.RETRY_SCHEDULED && retry == null) {
                retry = new PushRetryException(jobId, outcome);
            }
        }
        if (retry != null) {
            throw retry;
        }
    }

    private void deliver(EventEnvelope<NotificationPushRequestData> envelope, ConsumerRecord<Object, Object> record) {
        if (!NotificationPushMessages.BULK_PUSH_REQUESTED.equals(envelope.eventType())) {
            log.warn("[BULK_PUSH_REQUEST_UNKNOWN_EVENT] eventId={}, eventType={}",
                    envelope.eventId(), envelope.eventType());
            return;
        }
        envelope.requireSchemaVersion(NotificationPushMessages.SCHEMA_VERSION);
        long jobId = envelope.data().jobId();
        PushDeliveryOutcome outcome = pushService.deliver(jobId, NotificationBulkPushConsumerConfig.MAX_ATTEMPTS);
        if (outcome == PushDeliveryOutcome.RETRY_SCHEDULED) {
            throw new PushRetryException(jobId, outcome);
        }
        if (outcome == PushDeliveryOutcome.RETRIES_EXHAUSTED) {
            sendToDeadLetter(record, new PushRetryException(jobId, outcome));
            return;
        }
        log.info("[BULK_PUSH_DELIVERED] eventId={}, jobId={}, outcome={}", envelope.eventId(), jobId, outcome);
    }

    private EventEnvelope<NotificationPushRequestData> read(ConsumerRecord<Object, Object> record) {
        try {
            return jsonMapper.readValue(String.valueOf(record.value()), envelopeType);
        } catch (JacksonException e) {
            if (e.getCause() instanceof InvalidEventException invalid) {
                throw invalid;
            }
            throw new InvalidEventException("읽을 수 없는 메시지입니다. " + e.getOriginalMessage());
        }
    }

    private void sendToDeadLetter(ConsumerRecord<Object, Object> record, Exception exception) {
        log.error("[BULK_PUSH_DEAD_LETTER] topic={}, partition={}, offset={}",
                record.topic(), record.partition(), record.offset(), exception);
        deadLetter.accept(record, exception);
    }

    Duration retryDelay(RecordContext<Object, Object> record) {
        double factor = Math.pow(NotificationBulkPushConsumerConfig.RETRY_MULTIPLIER,
                Math.max(0, record.getNumberOfFailedAttempts() - 1));
        long delay = (long) (kafkaProperties.bulkRetryInitialInterval().toMillis() * factor);
        return Duration.ofMillis(Math.min(delay, NotificationBulkPushConsumerConfig.RETRY_MAX_INTERVAL_MILLIS));
    }
}
