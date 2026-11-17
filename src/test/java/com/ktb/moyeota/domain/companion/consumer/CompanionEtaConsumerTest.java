package com.ktb.moyeota.domain.companion.consumer;

import static org.assertj.core.api.Assertions.assertThat;

import com.ktb.moyeota.domain.companion.message.CompanionChangedMessages;
import com.ktb.moyeota.domain.companion.service.CompanionEtaService;
import com.ktb.moyeota.domain.companion.service.EtaUnavailableException;
import com.ktb.moyeota.global.config.KafkaConfig;
import com.ktb.moyeota.global.config.KafkaTestContainerConfig;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.apache.kafka.clients.admin.NewTopic;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.jackson.autoconfigure.JacksonAutoConfiguration;
import org.springframework.boot.kafka.autoconfigure.KafkaAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnectionAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.kafka.core.KafkaTemplate;

@SpringBootTest(
        classes = {KafkaConfig.class, CompanionEtaConsumer.class, CompanionEtaConsumerConfig.class,
                KafkaTestContainerConfig.class, CompanionEtaConsumerTest.ListenerConfig.class},
        properties = "moyeota.chat.system-message.mode=kafka")
@ImportAutoConfiguration({
        JacksonAutoConfiguration.class,
        KafkaAutoConfiguration.class,
        ServiceConnectionAutoConfiguration.class})
class CompanionEtaConsumerTest {

    @Autowired
    private KafkaTemplate<String, String> kafkaTemplate;

    @Autowired
    private RecordingEtaService etaService;

    @Test
    @DisplayName("운행 시작 사실을 받으면 발생 시각을 운행 시작 시각으로 삼아 도착 예정 시각을 구한다")
    void estimatesOnRideStarted() throws Exception {
        send(9101L, CompanionChangedMessages.RIDE_STARTED, "2026-11-17T09:00:00.000+09:00");

        assertThat(etaService.await(9101L)).isEqualTo(LocalDateTime.of(2026, 11, 17, 9, 0));
    }

    @Test
    @DisplayName("운행 시작이 아닌 사실은 넘긴다")
    void skipsOtherFacts() throws Exception {
        send(9102L, CompanionChangedMessages.PARTICIPANT_JOINED, "2026-11-17T09:00:00.000+09:00");
        send(9103L, CompanionChangedMessages.RIDE_STARTED, "2026-11-17T09:05:00.000+09:00");

        etaService.await(9103L);
        assertThat(etaService.estimated).doesNotContainKey(9102L);
    }

    @Test
    @DisplayName("길찾기가 실패하면 간격을 두고 다시 받아 처리한다")
    void retriesWhenNaviUnavailable() throws Exception {
        etaService.failFirst(9104L);

        send(9104L, CompanionChangedMessages.RIDE_STARTED, "2026-11-17T09:10:00.000+09:00");

        assertThat(etaService.await(9104L)).isEqualTo(LocalDateTime.of(2026, 11, 17, 9, 10));
        assertThat(etaService.attempts(9104L)).isEqualTo(2);
    }

    private void send(Long companionId, String eventType, String occurredAt) throws Exception {
        String value = """
                {"event_id":"%s","event_type":"%s","schema_version":1,"occurred_at":"%s",
                 "data":{"companion_id":%d,"kind":"TAXI_POT","user_id":null}}
                """.formatted(UUID.randomUUID(), eventType, occurredAt, companionId);
        kafkaTemplate.send(CompanionChangedMessages.TOPIC, CompanionChangedMessages.key(companionId), value)
                .get(10, TimeUnit.SECONDS);
    }

    static class RecordingEtaService extends CompanionEtaService {

        private final Map<Long, LocalDateTime> estimated = new ConcurrentHashMap<>();
        private final Map<Long, AtomicInteger> attempts = new ConcurrentHashMap<>();
        private final BlockingQueue<Long> failOnce = new LinkedBlockingQueue<>();

        RecordingEtaService() {
            super(null, null, null);
        }

        void failFirst(Long companionId) {
            failOnce.add(companionId);
        }

        @Override
        public boolean estimate(Long companionId, LocalDateTime startedAt) {
            attempts.computeIfAbsent(companionId, ignored -> new AtomicInteger()).incrementAndGet();
            if (failOnce.remove(companionId)) {
                throw new EtaUnavailableException(companionId);
            }
            estimated.put(companionId, startedAt);
            return true;
        }

        LocalDateTime await(Long companionId) throws InterruptedException {
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(60);
            while (System.nanoTime() < deadline) {
                LocalDateTime startedAt = estimated.get(companionId);
                if (startedAt != null) {
                    return startedAt;
                }
                Thread.sleep(200);
            }
            throw new AssertionError("companion_id=" + companionId + " 도착 예정 시각을 구하지 않았습니다");
        }

        int attempts(Long companionId) {
            return attempts.get(companionId).get();
        }
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class ListenerConfig {

        @Bean
        NewTopic companionChangedTopic() {
            return new NewTopic(CompanionChangedMessages.TOPIC, 1, (short) 1);
        }

        @Bean
        NewTopic companionChangedDeadLetterTopic() {
            return new NewTopic(CompanionChangedMessages.TOPIC + "-dlt", 1, (short) 1);
        }

        @Bean
        RecordingEtaService companionEtaService() {
            return new RecordingEtaService();
        }
    }
}
