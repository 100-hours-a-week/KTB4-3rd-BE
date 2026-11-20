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

@Slf4j
@Configuration
@ConditionalOnServerRole({ServerRole.WORKER, ServerRole.ALL})
@ConditionalOnProperty(prefix = "moyeota.notification.push", name = "mode", havingValue = "kafka")
@ConditionalOnProperty(prefix = NotificationBulkPushConsumerProperties.PREFIX,
        name = NotificationBulkPushConsumerProperties.TYPE, havingValue = "batch")
public class NotificationBulkPushBatchConsumerConfig {

    public static final String CONTAINER_FACTORY = "notificationBulkPushBatchContainerFactory";

    @Bean(CONTAINER_FACTORY)
    public ConcurrentKafkaListenerContainerFactory<Object, Object> notificationBulkPushBatchContainerFactory(
            ConcurrentKafkaListenerContainerFactoryConfigurer configurer,
            ConsumerFactory<Object, Object> consumerFactory,
            KafkaOperations<?, ?> kafkaOperations,
            NotificationPushKafkaProperties properties) {
        ConcurrentKafkaListenerContainerFactory<Object, Object> factory = new ConcurrentKafkaListenerContainerFactory<>();
        configurer.configure(factory, consumerFactory);
        factory.setBatchListener(true);
        factory.setConcurrency(properties.bulkConcurrency());
        DeadLetterPublishingRecoverer deadLetter = new DeadLetterPublishingRecoverer(kafkaOperations,
                (record, exception) -> new TopicPartition(record.topic() + RetryTopicConstants.DEFAULT_DLT_SUFFIX, -1));
        DefaultErrorHandler errorHandler = new DefaultErrorHandler((record, exception) -> {
            log.error("[BULK_PUSH_DEAD_LETTER] topic={}, partition={}, offset={}",
                    record.topic(), record.partition(), record.offset(), exception);
            deadLetter.accept(record, exception);
        }, NotificationBulkPushConsumerConfig.backOff(properties));
        errorHandler.addNotRetryableExceptions(InvalidEventException.class);
        factory.setCommonErrorHandler(errorHandler);
        return factory;
    }
}
