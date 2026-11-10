package com.ktb.moyeota.global.config;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.test.context.DynamicPropertyRegistrar;
import org.testcontainers.kafka.KafkaContainer;
import org.testcontainers.utility.DockerImageName;

@TestConfiguration(proxyBeanMethods = false)
public class KafkaTestContainerConfig {

    public static final KafkaContainer KAFKA = new KafkaContainer(DockerImageName.parse("apache/kafka:4.2.0"));

    static {
        KAFKA.start();
    }

    @Bean
    DynamicPropertyRegistrar kafkaContainerProperties() {
        return registry -> registry.add("spring.kafka.bootstrap-servers", KAFKA::getBootstrapServers);
    }
}
