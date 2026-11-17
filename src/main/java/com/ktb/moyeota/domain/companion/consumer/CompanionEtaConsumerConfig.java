package com.ktb.moyeota.domain.companion.consumer;

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
import org.springframework.util.backoff.ExponentialBackOff;

@Slf4j
@Configuration
@ConditionalOnServerRole({ServerRole.WORKER, ServerRole.ALL})
@ConditionalOnProperty(prefix = "moyeota.chat.system-message", name = "mode", havingValue = "kafka")
public class CompanionEtaConsumerConfig {

    public static final String CONTAINER_FACTORY = "companionEtaContainerFactory";
    static final long INITIAL_RETRY_INTERVAL_MILLIS = 2_000L;
    static final double RETRY_MULTIPLIER = 2.0;
    static final long MAX_RETRY_INTERVAL_MILLIS = 60_000L;
    static final long MAX_RETRY_ELAPSED_MILLIS = 300_000L;

    @Bean(CONTAINER_FACTORY)
    public ConcurrentKafkaListenerContainerFactory<Object, Object> companionEtaContainerFactory(
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
        ExponentialBackOff backOff = new ExponentialBackOff(INITIAL_RETRY_INTERVAL_MILLIS, RETRY_MULTIPLIER);
        backOff.setMaxInterval(MAX_RETRY_INTERVAL_MILLIS);
        backOff.setMaxElapsedTime(MAX_RETRY_ELAPSED_MILLIS);
        DefaultErrorHandler errorHandler = new DefaultErrorHandler((record, exception) -> {
            log.error("[COMPANION_ETA_DEAD_LETTER] topic={}, partition={}, offset={}, key={}",
                    record.topic(), record.partition(), record.offset(), record.key(), exception);
            deadLetter.accept(record, exception);
        }, backOff);
        errorHandler.addNotRetryableExceptions(InvalidEventException.class);
        return errorHandler;
    }
}
