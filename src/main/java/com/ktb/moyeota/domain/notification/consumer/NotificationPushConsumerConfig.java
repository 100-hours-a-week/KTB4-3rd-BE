package com.ktb.moyeota.domain.notification.consumer;

import com.ktb.moyeota.global.config.ConditionalOnServerRole;
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
import org.springframework.util.backoff.FixedBackOff;

@Slf4j
@Configuration
@ConditionalOnServerRole({ServerRole.WORKER, ServerRole.ALL})
@ConditionalOnProperty(prefix = "moyeota.notification.push", name = "mode", havingValue = "kafka")
public class NotificationPushConsumerConfig {

    public static final String CONTAINER_FACTORY = "notificationPushContainerFactory";
    static final long RETRY_INTERVAL_MILLIS = 10_000L;
    static final long MAX_RETRIES = 1L;
    static final int MAX_ATTEMPTS = (int) MAX_RETRIES + 1;

    @Bean(CONTAINER_FACTORY)
    public ConcurrentKafkaListenerContainerFactory<Object, Object> notificationPushContainerFactory(
            ConcurrentKafkaListenerContainerFactoryConfigurer configurer,
            ConsumerFactory<Object, Object> consumerFactory,
            KafkaOperations<?, ?> kafkaOperations) {
        ConcurrentKafkaListenerContainerFactory<Object, Object> factory = new ConcurrentKafkaListenerContainerFactory<>();
        configurer.configure(factory, consumerFactory);
        factory.setCommonErrorHandler(errorHandler(kafkaOperations));
        return factory;
    }

    private static DefaultErrorHandler errorHandler(KafkaOperations<?, ?> kafkaOperations) {
        DeadLetterPublishingRecoverer deadLetter = new DeadLetterPublishingRecoverer(kafkaOperations,
                (record, exception) -> new TopicPartition(record.topic() + RetryTopicConstants.DEFAULT_DLT_SUFFIX, -1));
        DefaultErrorHandler errorHandler = new DefaultErrorHandler((record, exception) -> {
            log.error("[PUSH_DEAD_LETTER] topic={}, partition={}, offset={}",
                    record.topic(), record.partition(), record.offset(), exception);
            deadLetter.accept(record, exception);
        }, new FixedBackOff(RETRY_INTERVAL_MILLIS, MAX_RETRIES));
        errorHandler.addNotRetryableExceptions(InvalidEventException.class);
        return errorHandler;
    }
}
