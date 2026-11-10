package com.ktb.moyeota.global.config;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.test.context.DynamicPropertyRegistrar;
import org.testcontainers.mongodb.MongoDBContainer;
import org.testcontainers.utility.DockerImageName;

@TestConfiguration(proxyBeanMethods = false)
public class MongoTestContainerConfig {

    public static final MongoDBContainer MONGO = new MongoDBContainer(DockerImageName.parse("mongo:8.0"))
            .withEnv("GLIBC_TUNABLES", "glibc.pthread.rseq=1");

    static {
        MONGO.start();
    }

    @Bean
    DynamicPropertyRegistrar mongoContainerProperties() {
        return registry -> registry.add("spring.mongodb.uri", MONGO::getReplicaSetUrl);
    }
}
