package com.ktb.moyeota.global.config;

import com.ktb.moyeota.global.event.InvalidEventException;
import com.ktb.moyeota.global.trace.TraceIdProducerInterceptor;
import java.util.Map;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.TopicPartition;
import org.springframework.boot.kafka.autoconfigure.DefaultKafkaProducerFactoryCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.KafkaOperations;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.retrytopic.RetryTopicConstants;
import org.springframework.kafka.support.converter.RecordMessageConverter;
import org.springframework.kafka.support.converter.StringJacksonJsonMessageConverter;
import tools.jackson.databind.json.JsonMapper;

@Configuration
public class KafkaConfig {

    @Bean
    public RecordMessageConverter kafkaMessageConverter(JsonMapper jsonMapper) {
        return new StringJacksonJsonMessageConverter(jsonMapper);
    }

    @Bean
    public DefaultKafkaProducerFactoryCustomizer traceIdProducerInterceptor() {
        return factory -> factory.updateConfigs(
                Map.of(ProducerConfig.INTERCEPTOR_CLASSES_CONFIG, TraceIdProducerInterceptor.class.getName()));
    }

    @Bean
    public DefaultErrorHandler kafkaErrorHandler(KafkaOperations<?, ?> kafkaOperations) {
        DeadLetterPublishingRecoverer recoverer = new DeadLetterPublishingRecoverer(kafkaOperations,
                (record, exception) -> new TopicPartition(record.topic() + RetryTopicConstants.DEFAULT_DLT_SUFFIX, -1));
        DefaultErrorHandler errorHandler = new DefaultErrorHandler(recoverer);
        errorHandler.addNotRetryableExceptions(InvalidEventException.class);
        return errorHandler;
    }
}
