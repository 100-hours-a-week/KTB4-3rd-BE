package com.ktb.moyeota.domain.taxipot.consumer;

import static com.ktb.moyeota.fixture.MatchRequestFixture.DEPARTURE_AT;
import static com.ktb.moyeota.fixture.MatchRequestFixture.matchRequest;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.ktb.moyeota.domain.taxipot.message.MatchDecidedData;
import com.ktb.moyeota.domain.taxipot.message.MatchRequestData;
import com.ktb.moyeota.domain.taxipot.message.MatchingMessages;
import com.ktb.moyeota.domain.taxipot.service.MatchDecisionService;
import com.ktb.moyeota.global.config.KafkaConfig;
import com.ktb.moyeota.global.config.KafkaTestContainerConfig;
import com.ktb.moyeota.global.event.EventEnvelope;
import java.time.OffsetDateTime;
import java.util.List;
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
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import tools.jackson.databind.json.JsonMapper;

@SpringBootTest(
        classes = {KafkaConfig.class, MatchDecisionConsumerConfig.class, MatchDecisionConsumer.class,
                KafkaTestContainerConfig.class, MatchDecisionConsumerTest.ListenerConfig.class},
        properties = {
                "spring.kafka.consumer.auto-offset-reset=earliest",
                "moyeota.taxi-pot.matching.mode=kafka"})
@ImportAutoConfiguration({
        JacksonAutoConfiguration.class,
        KafkaAutoConfiguration.class,
        ServiceConnectionAutoConfiguration.class})
class MatchDecisionConsumerTest {

    private static final String DLT = MatchingMessages.DECISION_TOPIC + "-dlt";
    private static final String CELL_KEY = "2079:5606";

    @MockitoBean
    private MatchDecisionService matchDecisionService;

    @Autowired
    private KafkaTemplate<String, String> kafkaTemplate;

    @Autowired
    private JsonMapper jsonMapper;

    @Autowired
    private BlockingQueue<ConsumerRecord<String, String>> deadLetters;

    @Test
    @DisplayName("결정을 받으면 메시지 키(칸)와 결정 내용으로 반영 서비스를 부른다")
    void appliesDecision() throws Exception {
        String resultId = "101,102";

        send(MatchingMessages.DECIDED, MatchingMessages.SCHEMA_VERSION, decided(resultId));

        verify(matchDecisionService, timeout(30_000)).apply(eq(CELL_KEY),
                argThat(decided -> decided.resultId().equals(resultId)
                        && decided.requests().stream().map(MatchRequestData::requestId).toList()
                        .equals(List.of(101L, 102L))));
    }

    @Test
    @DisplayName("반영 중 예외가 나면 2초 간격으로 3번 더 시도하고, 그래도 실패하면 DLT 로 보낸다")
    void retriesThenSendsToDeadLetter() throws Exception {
        String resultId = "201,202";
        willThrow(new IllegalStateException("DB 오류")).given(matchDecisionService)
                .apply(any(), argThat(decided -> decided != null && decided.resultId().equals(resultId)));

        String eventId = send(MatchingMessages.DECIDED, MatchingMessages.SCHEMA_VERSION, decided(resultId));

        ConsumerRecord<String, String> deadLetter = awaitDeadLetter(eventId);
        assertThat(deadLetter.key()).isEqualTo(CELL_KEY);
        verify(matchDecisionService, times(4)).apply(any(),
                argThat(decided -> decided != null && decided.resultId().equals(resultId)));
    }

    @Test
    @DisplayName("읽지 못하는 버전은 반영하지 않고 바로 DLT 로 보낸다")
    void sendsUnsupportedVersionToDeadLetter() throws Exception {
        String resultId = "301,302";

        String eventId = send(MatchingMessages.DECIDED, 2, decided(resultId));

        awaitDeadLetter(eventId);
        verify(matchDecisionService, never()).apply(any(),
                argThat(decided -> decided != null && decided.resultId().equals(resultId)));
    }

    private static MatchDecidedData decided(String resultId) {
        List<Long> ids = List.of(resultId.split(",")).stream().map(Long::valueOf).toList();
        return new MatchDecidedData(resultId, resultId,
                ids.stream().map(id -> MatchRequestData.from(matchRequest(id))).toList(),
                ids.getFirst(), ids.getFirst(), DEPARTURE_AT);
    }

    private String send(String eventType, int schemaVersion, MatchDecidedData data) throws Exception {
        String eventId = UUID.randomUUID().toString();
        String value = jsonMapper.writeValueAsString(
                new EventEnvelope<>(eventId, eventType, schemaVersion, OffsetDateTime.now(), data));
        kafkaTemplate.send(MatchingMessages.DECISION_TOPIC, CELL_KEY, value).get(10, TimeUnit.SECONDS);
        return eventId;
    }

    private ConsumerRecord<String, String> awaitDeadLetter(String eventId) throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(60);
        while (System.nanoTime() < deadline) {
            ConsumerRecord<String, String> record = deadLetters.poll(1, TimeUnit.SECONDS);
            if (record != null && record.value().contains(eventId)) {
                return record;
            }
        }
        throw new AssertionError("DLT 에서 event_id=" + eventId + " 메시지를 받지 못했습니다");
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class ListenerConfig {

        @Bean
        NewTopic matchingDecisionTopic() {
            return new NewTopic(MatchingMessages.DECISION_TOPIC, 1, (short) 1);
        }

        @Bean
        NewTopic matchingDecisionDeadLetterTopic() {
            return new NewTopic(DLT, 1, (short) 1);
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

        @KafkaListener(topics = DLT, groupId = "match-decision-dlt-test")
        void listen(ConsumerRecord<String, String> record) {
            deadLetters.add(record);
        }
    }
}
