package com.ktb.moyeota.global.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.ktb.moyeota.global.trace.TraceId;
import com.ktb.moyeota.global.trace.TraceIdRecordInterceptor;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import org.apache.kafka.clients.admin.NewTopic;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.jackson.autoconfigure.JacksonAutoConfiguration;
import org.springframework.boot.kafka.autoconfigure.KafkaAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnectionAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;

@SpringBootTest(
        classes = {KafkaConfig.class, TraceIdRecordInterceptor.class, KafkaTestContainerConfig.class,
                KafkaTraceIdTest.ListenerConfig.class},
        properties = "spring.kafka.consumer.auto-offset-reset=earliest")
@ImportAutoConfiguration({
        JacksonAutoConfiguration.class,
        KafkaAutoConfiguration.class,
        ServiceConnectionAutoConfiguration.class})
class KafkaTraceIdTest {

    private static final String TOPIC = "kafka-trace-id-test";
    private static final String TRACE_ID = "Root=1-67891233-abcdef012345678912345678";

    @Autowired
    private KafkaTemplate<String, String> kafkaTemplate;

    @Autowired
    private BlockingQueue<String> receivedTraceIds;

    @AfterEach
    void tearDown() {
        MDC.clear();
    }

    @Test
    @DisplayName("보낸 쪽의 추적 ID 가 받는 쪽 리스너의 로그에 그대로 이어진다")
    void carriesTraceIdToListener() throws Exception {
        MDC.put(TraceId.LOG_KEY, TRACE_ID);

        kafkaTemplate.send(TOPIC, "101", "{\"spot_id\":101}").get(10, TimeUnit.SECONDS);

        assertThat(receivedTraceIds.poll(30, TimeUnit.SECONDS)).isEqualTo(TRACE_ID);
    }

    record SpotMessage(Long spotId) {
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class ListenerConfig {

        @Bean
        NewTopic kafkaTraceIdTestTopic() {
            return new NewTopic(TOPIC, 1, (short) 1);
        }

        @Bean
        BlockingQueue<String> receivedTraceIds() {
            return new LinkedBlockingQueue<>();
        }

        @Bean
        TraceIdListener traceIdListener(BlockingQueue<String> receivedTraceIds) {
            return new TraceIdListener(receivedTraceIds);
        }
    }

    static class TraceIdListener {

        private final BlockingQueue<String> receivedTraceIds;

        TraceIdListener(BlockingQueue<String> receivedTraceIds) {
            this.receivedTraceIds = receivedTraceIds;
        }

        @KafkaListener(topics = TOPIC, groupId = "kafka-trace-id-test")
        void listen(SpotMessage message) {
            receivedTraceIds.add(MDC.get(TraceId.LOG_KEY));
        }
    }
}
