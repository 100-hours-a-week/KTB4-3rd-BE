package com.ktb.moyeota.global.config;

import com.ktb.moyeota.global.trace.TraceIdProducerInterceptor;
import java.util.Map;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.springframework.boot.kafka.autoconfigure.DefaultKafkaProducerFactoryCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
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
}
