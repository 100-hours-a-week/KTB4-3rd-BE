package com.ktb.moyeota.domain.notification.consumer;

import com.ktb.moyeota.global.config.ConditionalOnServerRole;
import com.ktb.moyeota.global.config.NotificationPushKafkaProperties;
import com.ktb.moyeota.global.config.ServerRole;
import com.ktb.moyeota.global.event.InvalidEventException;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.common.TopicPartition;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.kafka.autoconfigure.ConcurrentKafkaListenerContainerFactoryConfigurer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.KafkaOperations;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.retrytopic.RetryTopicConstants;
import org.springframework.util.backoff.ExponentialBackOff;

@Slf4j
@Configuration
@ConditionalOnServerRole({ServerRole.WORKER, ServerRole.ALL})
@ConditionalOnProperty(prefix = "moyeota.notification.push", name = "mode", havingValue = "kafka")
public class NotificationBulkPushConsumerConfig {

    public static final String CONTAINER_FACTORY = "notificationBulkPushContainerFactory";
    static final int MAX_RETRIES = 4;
    static final int MAX_ATTEMPTS = MAX_RETRIES + 1;
    static final double RETRY_MULTIPLIER = 2.0;
    static final double RETRY_JITTER_RATIO = 0.2;
    static final long RETRY_MAX_INTERVAL_MILLIS = 60_000L;

    @Bean(CONTAINER_FACTORY)
    public ConcurrentKafkaListenerContainerFactory<Object, Object> notificationBulkPushContainerFactory(
            ConcurrentKafkaListenerContainerFactoryConfigurer configurer,
            ConsumerFactory<Object, Object> consumerFactory,
            KafkaOperations<?, ?> kafkaOperations,
            NotificationPushKafkaProperties properties) {
        ConcurrentKafkaListenerContainerFactory<Object, Object> factory = new ConcurrentKafkaListenerContainerFactory<>();
        configurer.configure(factory, consumerFactory);
        factory.setConcurrency(properties.bulkConcurrency());
        factory.setCommonErrorHandler(errorHandler(kafkaOperations, properties));
        return factory;
    }

    private static DefaultErrorHandler errorHandler(KafkaOperations<?, ?> kafkaOperations,
                                                    NotificationPushKafkaProperties properties) {
        DeadLetterPublishingRecoverer deadLetter = new DeadLetterPublishingRecoverer(kafkaOperations,
                (record, exception) -> new TopicPartition(record.topic() + RetryTopicConstants.DEFAULT_DLT_SUFFIX, -1));
        DefaultErrorHandler errorHandler = new DefaultErrorHandler((record, exception) -> {
            log.error("[BULK_PUSH_DEAD_LETTER] topic={}, partition={}, offset={}",
                    record.topic(), record.partition(), record.offset(), exception);
            deadLetter.accept(record, exception);
        }, backOff(properties));
        errorHandler.addNotRetryableExceptions(InvalidEventException.class);
        return errorHandler;
    }

    static ExponentialBackOff backOff(NotificationPushKafkaProperties properties) {
        ExponentialBackOff backOff = new ExponentialBackOff(properties.bulkRetryInitialInterval().toMillis(),
                RETRY_MULTIPLIER);
        backOff.setMaxAttempts(MAX_RETRIES);
        backOff.setMaxInterval(RETRY_MAX_INTERVAL_MILLIS);
        backOff.setJitter((long) (properties.bulkRetryInitialInterval().toMillis() * RETRY_JITTER_RATIO));
        return backOff;
    }
}
