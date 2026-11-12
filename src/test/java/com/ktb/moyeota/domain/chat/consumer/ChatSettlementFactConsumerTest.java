package com.ktb.moyeota.domain.chat.consumer;

import static org.assertj.core.api.Assertions.assertThat;

import com.ktb.moyeota.domain.chat.service.ChatSettlementFactWriter;
import com.ktb.moyeota.domain.settlement.message.SettlementChangedData;
import com.ktb.moyeota.domain.settlement.message.SettlementChangedMessages;
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
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.jackson.autoconfigure.JacksonAutoConfiguration;
import org.springframework.boot.kafka.autoconfigure.KafkaAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnectionAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaAdmin;
import org.springframework.kafka.core.KafkaTemplate;

@SpringBootTest(
        classes = {KafkaConfig.class, ChatSettlementFactConsumer.class, KafkaTestContainerConfig.class,
                ChatSettlementFactConsumerTest.ListenerConfig.class},
        properties = "moyeota.chat.system-message.mode=kafka")
@ImportAutoConfiguration({
        JacksonAutoConfiguration.class,
        KafkaAutoConfiguration.class,
        ServiceConnectionAutoConfiguration.class})
class ChatSettlementFactConsumerTest {

    private static final String DLT = SettlementChangedMessages.TOPIC + "-dlt";

    @Autowired
    private KafkaTemplate<String, String> kafkaTemplate;

    @Autowired
    private BlockingQueue<Written> written;

    @Autowired
    private BlockingQueue<ConsumerRecord<String, String>> deadLetters;

    @Test
    @DisplayName("소비자 그룹이 처음 붙기 전에 토픽에 들어온 사실도 처음부터 읽어 처리한다")
    void writesFactPublishedBeforeGroupJoined() throws Exception {
        assertThat(awaitWritten(EarlyFact.EVENT_ID).eventType()).isEqualTo(SettlementChangedMessages.COMPLETED);
    }

    @Test
    @DisplayName("채팅이 처리하는 정산 바뀜은 이벤트 ID · 종류 · 데이터를 그대로 넘긴다")
    void writesSupportedEvent() throws Exception {
        String eventId = send(401L, SettlementChangedMessages.TRANSFER_REQUESTED, 1, """
                {"settlement_id":11,"companion_id":401}
                """);

        assertThat(awaitWritten(eventId)).isEqualTo(new Written(eventId,
                SettlementChangedMessages.TRANSFER_REQUESTED, new SettlementChangedData(11L, 401L)));
    }

    @Test
    @DisplayName("채팅이 처리하지 않는 종류는 넘기고 다음 메시지를 그대로 처리한다")
    void skipsUnsupportedEvent() throws Exception {
        String skipped = send(402L, "settlement.reminded", 1, """
                {"settlement_id":12,"companion_id":402}
                """);
        String next = send(402L, SettlementChangedMessages.COMPLETED, 1, """
                {"settlement_id":12,"companion_id":402}
                """);

        awaitWritten(next);
        assertThat(written).noneMatch(each -> each.eventId().equals(skipped));
    }

    @Test
    @DisplayName("읽지 못하는 버전은 처리하지 않고 바로 DLT 로 보낸다")
    void sendsUnsupportedVersionToDeadLetter() throws Exception {
        String eventId = send(403L, SettlementChangedMessages.COMPLETED, 2, """
                {"settlement_id":13,"companion_id":403}
                """);

        assertThat(awaitDeadLetter(eventId).key()).isEqualTo("403");
        assertThat(written).noneMatch(each -> each.eventId().equals(eventId));
    }

    @Test
    @DisplayName("settlement_id 가 없으면 처리하지 않고 바로 DLT 로 보낸다")
    void sendsMissingSettlementToDeadLetter() throws Exception {
        String eventId = send(404L, SettlementChangedMessages.COMPLETED, 1, """
                {"companion_id":404}
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
        kafkaTemplate.send(SettlementChangedMessages.TOPIC, SettlementChangedMessages.key(companionId), value)
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

    record Written(String eventId, String eventType, SettlementChangedData data) {
    }

    static class RecordingWriter extends ChatSettlementFactWriter {

        private final BlockingQueue<Written> written;

        RecordingWriter(BlockingQueue<Written> written) {
            super(null, null, null, null);
            this.written = written;
        }

        @Override
        public boolean write(String eventId, String eventType, SettlementChangedData data) {
            written.add(new Written(eventId, eventType, data));
            return true;
        }
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class ListenerConfig {

        @Bean
        NewTopic settlementChangedTopic() {
            return new NewTopic(SettlementChangedMessages.TOPIC, 1, (short) 1);
        }

        @Bean
        NewTopic settlementChangedDeadLetterTopic() {
            return new NewTopic(DLT, 1, (short) 1);
        }

        @Bean
        BlockingQueue<Written> written() {
            return new LinkedBlockingQueue<>();
        }

        @Bean
        ChatSettlementFactWriter chatSettlementFactWriter(BlockingQueue<Written> written) {
            return new RecordingWriter(written);
        }

        @Bean
        EarlyFact earlyFact(KafkaAdmin kafkaAdmin, KafkaTemplate<String, String> kafkaTemplate) {
            return new EarlyFact(kafkaAdmin, kafkaTemplate);
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

    static class EarlyFact implements SmartInitializingSingleton {

        static final String EVENT_ID = UUID.randomUUID().toString();

        private final KafkaAdmin kafkaAdmin;
        private final KafkaTemplate<String, String> kafkaTemplate;

        EarlyFact(KafkaAdmin kafkaAdmin, KafkaTemplate<String, String> kafkaTemplate) {
            this.kafkaAdmin = kafkaAdmin;
            this.kafkaTemplate = kafkaTemplate;
        }

        @Override
        public void afterSingletonsInstantiated() {
            kafkaAdmin.initialize();
            String value = """
                    {"event_id":"%s","event_type":"%s","schema_version":1,
                     "occurred_at":"2026-11-11T09:00:00.000+09:00","data":{"settlement_id":10,"companion_id":400}}
                    """.formatted(EVENT_ID, SettlementChangedMessages.COMPLETED);
            try {
                kafkaTemplate.send(SettlementChangedMessages.TOPIC, SettlementChangedMessages.key(400L), value)
                        .get(10, TimeUnit.SECONDS);
            } catch (Exception e) {
                throw new IllegalStateException(e);
            }
        }
    }

    static class DeadLetterListener {

        private final BlockingQueue<ConsumerRecord<String, String>> deadLetters;

        DeadLetterListener(BlockingQueue<ConsumerRecord<String, String>> deadLetters) {
            this.deadLetters = deadLetters;
        }

        @KafkaListener(topics = DLT, groupId = "settlement-changed-dlt-test", properties = "auto.offset.reset=earliest")
        void listen(ConsumerRecord<String, String> record) {
            deadLetters.add(record);
        }
    }
}
