package com.ktb.moyeota.domain.chat.consumer;

import static org.assertj.core.api.Assertions.assertThat;

import com.ktb.moyeota.domain.chat.service.ChatCompanionFactWriter;
import com.ktb.moyeota.domain.companion.entity.CompanionKind;
import com.ktb.moyeota.domain.companion.message.CompanionChangedData;
import com.ktb.moyeota.domain.companion.message.CompanionChangedMessages;
import com.ktb.moyeota.global.config.KafkaConfig;
import com.ktb.moyeota.global.config.KafkaTestContainerConfig;
import java.util.UUID;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.clients.consumer.ConsumerRecord;
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
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;

@SpringBootTest(
        classes = {KafkaConfig.class, ChatCompanionFactConsumer.class, KafkaTestContainerConfig.class,
                ChatCompanionFactConsumerTest.ListenerConfig.class},
        properties = {
                "spring.kafka.consumer.auto-offset-reset=earliest",
                "moyeota.chat.system-message.mode=kafka"})
@ImportAutoConfiguration({
        JacksonAutoConfiguration.class,
        KafkaAutoConfiguration.class,
        ServiceConnectionAutoConfiguration.class})
class ChatCompanionFactConsumerTest {

    private static final String DLT = CompanionChangedMessages.TOPIC + "-dlt";

    @Autowired
    private KafkaTemplate<String, String> kafkaTemplate;

    @Autowired
    private BlockingQueue<Written> written;

    @Autowired
    private BlockingQueue<ConsumerRecord<String, String>> deadLetters;

    @Test
    @DisplayName("채팅이 처리하는 동행 바뀜은 이벤트 ID · 종류 · 데이터를 그대로 넘긴다")
    void writesSupportedEvent() throws Exception {
        String eventId = send(301L, CompanionChangedMessages.PARTICIPANT_JOINED, 1,
                """
                {"companion_id":301,"kind":"TAXI_POT","user_id":7}
                """);

        assertThat(awaitWritten(eventId)).isEqualTo(new Written(eventId, CompanionChangedMessages.PARTICIPANT_JOINED,
                new CompanionChangedData(301L, CompanionKind.TAXI_POT, 7L)));
    }

    @Test
    @DisplayName("채팅이 처리하지 않는 종류는 넘기고 다음 메시지를 그대로 처리한다")
    void skipsUnsupportedEvent() throws Exception {
        String skipped = send(302L, "companion.canceled", 1, """
                {"companion_id":302,"kind":"CARPOOL","user_id":null}
                """);
        String next = send(302L, CompanionChangedMessages.RIDE_ENDED, 1, """
                {"companion_id":302,"kind":"CARPOOL","user_id":null}
                """);

        awaitWritten(next);
        assertThat(written).noneMatch(each -> each.eventId().equals(skipped));
    }

    @Test
    @DisplayName("읽지 못하는 버전은 처리하지 않고 바로 DLT 로 보낸다")
    void sendsUnsupportedVersionToDeadLetter() throws Exception {
        String eventId = send(303L, CompanionChangedMessages.RIDE_STARTED, 2, """
                {"companion_id":303,"kind":"TAXI_POT","user_id":null}
                """);

        assertThat(awaitDeadLetter(eventId).key()).isEqualTo("303");
        assertThat(written).noneMatch(each -> each.eventId().equals(eventId));
    }

    @Test
    @DisplayName("companion_id 가 없으면 처리하지 않고 바로 DLT 로 보낸다")
    void sendsMissingCompanionToDeadLetter() throws Exception {
        String eventId = send(304L, CompanionChangedMessages.RIDE_STARTED, 1, """
                {"kind":"TAXI_POT","user_id":null}
                """);

        awaitDeadLetter(eventId);
        assertThat(written).noneMatch(each -> each.eventId().equals(eventId));
    }

    private String send(Long companionId, String eventType, int schemaVersion, String data) throws Exception {
        String eventId = UUID.randomUUID().toString();
        String value = """
                {"event_id":"%s","event_type":"%s","schema_version":%d,
                 "occurred_at":"2026-11-10T21:00:00.000+09:00","data":%s}
                """.formatted(eventId, eventType, schemaVersion, data.strip());
        kafkaTemplate.send(CompanionChangedMessages.TOPIC, CompanionChangedMessages.key(companionId), value)
                .get(10, TimeUnit.SECONDS);
        return eventId;
    }

    private Written awaitWritten(String eventId) throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(60);
        while (System.nanoTime() < deadline) {
            for (Written each : written) {
                if (each.eventId().equals(eventId)) {
                    return each;
                }
            }
            Thread.sleep(200);
        }
        throw new AssertionError("event_id=" + eventId + " 사실이 처리되지 않았습니다");
    }

    private ConsumerRecord<String, String> awaitDeadLetter(String eventId) throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(60);
        while (System.nanoTime() < deadline) {
            for (ConsumerRecord<String, String> record : deadLetters) {
                if (record.value().contains(eventId)) {
                    return record;
                }
            }
            Thread.sleep(200);
        }
        throw new AssertionError("DLT 에서 event_id=" + eventId + " 메시지를 받지 못했습니다");
    }

    record Written(String eventId, String eventType, CompanionChangedData data) {
    }

    static class RecordingWriter extends ChatCompanionFactWriter {

        private final BlockingQueue<Written> written;

        RecordingWriter(BlockingQueue<Written> written) {
            super(null, null, null);
            this.written = written;
        }

        @Override
        public boolean write(String eventId, String eventType, CompanionChangedData data) {
            written.add(new Written(eventId, eventType, data));
            return true;
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
            return new NewTopic(DLT, 1, (short) 1);
        }

        @Bean
        BlockingQueue<Written> written() {
            return new LinkedBlockingQueue<>();
        }

        @Bean
        ChatCompanionFactWriter chatCompanionFactWriter(BlockingQueue<Written> written) {
            return new RecordingWriter(written);
        }

        @Bean
        BlockingQueue<ConsumerRecord<String, String>> deadLetters() {
            return new LinkedBlockingQueue<>();
        }

        @Bean
        DeadLetterListener deadLetterListener(BlockingQueue<ConsumerRecord<String, String>> deadLetters) {
            return new DeadLetterListener(deadLetters);
        }
    }

    static class DeadLetterListener {

        private final BlockingQueue<ConsumerRecord<String, String>> deadLetters;

        DeadLetterListener(BlockingQueue<ConsumerRecord<String, String>> deadLetters) {
            this.deadLetters = deadLetters;
        }

        @KafkaListener(topics = DLT, groupId = "companion-changed-dlt-test")
        void listen(ConsumerRecord<String, String> record) {
            deadLetters.add(record);
        }
    }
}
