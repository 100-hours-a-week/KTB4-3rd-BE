package com.ktb.moyeota.domain.notification.consumer;

import com.ktb.moyeota.domain.notification.message.NotificationPushMessages;
import com.ktb.moyeota.domain.notification.message.NotificationPushRequestData;
import com.ktb.moyeota.domain.notification.service.NotificationPushService;
import com.ktb.moyeota.domain.notification.service.PushDeliveryOutcome;
import com.ktb.moyeota.global.config.ConditionalOnServerRole;
import com.ktb.moyeota.global.config.ServerRole;
import com.ktb.moyeota.global.event.EventEnvelope;
import com.ktb.moyeota.global.event.InvalidEventException;
import io.confluent.parallelconsumer.ParallelConsumerOptions;
import io.confluent.parallelconsumer.ParallelConsumerOptions.ProcessingOrder;
import io.confluent.parallelconsumer.ParallelStreamProcessor;
import io.confluent.parallelconsumer.PollContext;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Duration;
import java.util.List;
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
@ConditionalOnProperty(prefix = NotificationPushConsumerProperties.PREFIX,
        name = NotificationPushConsumerProperties.TYPE, havingValue = "parallel")
public class NotificationPushParallelConsumer implements SmartLifecycle {

    private static final String CLIENT_ID_PREFIX = "notification-push-parallel";
    private static final Duration DRAIN_TIMEOUT = Duration.ofSeconds(30);
    private static final Duration RETRY_DELAY = Duration.ofMillis(NotificationPushConsumerConfig.RETRY_INTERVAL_MILLIS);

    private final ConsumerFactory<Object, Object> consumerFactory;
    private final NotificationPushService pushService;
    private final NotificationPushParallelProperties properties;
    private final DeadLetterPublishingRecoverer deadLetter;
    private final JsonMapper jsonMapper;
    private final JavaType envelopeType;
    private final ObjectProvider<MeterRegistry> meterRegistry;

    private volatile ParallelStreamProcessor<Object, Object> processor;

    public NotificationPushParallelConsumer(
            ConsumerFactory<Object, Object> consumerFactory,
            KafkaOperations<?, ?> kafkaOperations,
            NotificationPushService pushService,
            NotificationPushParallelProperties properties,
            JsonMapper jsonMapper,
            ObjectProvider<MeterRegistry> meterRegistry) {
        this.consumerFactory = consumerFactory;
        this.pushService = pushService;
        this.properties = properties;
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
                NotificationPushConsumer.GROUP_ID, CLIENT_ID_PREFIX, null, overrides);
        ParallelConsumerOptions.ParallelConsumerOptionsBuilder<Object, Object> options =
                ParallelConsumerOptions.<Object, Object>builder()
                        .consumer(consumer)
                        .ordering(ProcessingOrder.UNORDERED)
                        .maxConcurrency(properties.concurrency())
                        .retryDelayProvider(record -> RETRY_DELAY);
        meterRegistry.ifAvailable(options::meterRegistry);
        ParallelStreamProcessor<Object, Object> created =
                ParallelStreamProcessor.createEosStreamProcessor(options.build());
        created.subscribe(List.of(NotificationPushMessages.PUSH_TOPIC));
        created.poll(this::consume);
        processor = created;
        log.info("[PUSH_PARALLEL_STARTED] concurrency={}", properties.concurrency());
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
            deliver(read(record));
        } catch (InvalidEventException e) {
            sendToDeadLetter(record, e);
        } catch (RuntimeException e) {
            if (attempt < NotificationPushConsumerConfig.MAX_ATTEMPTS) {
                throw e;
            }
            sendToDeadLetter(record, e);
        }
    }

    private void deliver(EventEnvelope<NotificationPushRequestData> envelope) {
        if (!NotificationPushMessages.PUSH_REQUESTED.equals(envelope.eventType())) {
            log.warn("[PUSH_REQUEST_UNKNOWN_EVENT] eventId={}, eventType={}", envelope.eventId(), envelope.eventType());
            return;
        }
        envelope.requireSchemaVersion(NotificationPushMessages.SCHEMA_VERSION);
        long jobId = envelope.data().jobId();
        PushDeliveryOutcome outcome = pushService.deliver(jobId, NotificationPushConsumerConfig.MAX_ATTEMPTS);
        if (outcome.retryable()) {
            throw new PushRetryException(jobId, outcome);
        }
        log.info("[PUSH_DELIVERED] eventId={}, jobId={}, outcome={}", envelope.eventId(), jobId, outcome);
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
        log.error("[PUSH_DEAD_LETTER] topic={}, partition={}, offset={}",
                record.topic(), record.partition(), record.offset(), exception);
        deadLetter.accept(record, exception);
    }
}
