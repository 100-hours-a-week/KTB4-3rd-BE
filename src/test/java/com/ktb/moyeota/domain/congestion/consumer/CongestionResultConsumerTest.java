package com.ktb.moyeota.domain.congestion.consumer;

import static org.assertj.core.api.Assertions.assertThat;

import com.ktb.moyeota.domain.congestion.config.CongestionStatusProperties;
import com.ktb.moyeota.domain.congestion.config.CongestionWindowProperties;
import com.ktb.moyeota.domain.congestion.document.Spot;
import com.ktb.moyeota.domain.congestion.message.CongestionMessages;
import com.ktb.moyeota.domain.congestion.repository.CongestionHistoryRepository;
import com.ktb.moyeota.domain.congestion.repository.SpotRepository;
import com.ktb.moyeota.domain.congestion.service.CongestionResultService;
import com.ktb.moyeota.domain.congestion.status.CongestionStatus;
import com.ktb.moyeota.domain.congestion.status.CongestionStatusMapper;
import com.ktb.moyeota.domain.congestion.window.CongestionWindowCalculator;
import com.ktb.moyeota.global.config.KafkaConfig;
import com.ktb.moyeota.global.config.KafkaTestContainerConfig;
import com.ktb.moyeota.global.config.MongoTestContainerConfig;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.bson.Document;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.data.mongodb.autoconfigure.DataMongoAutoConfiguration;
import org.springframework.boot.jackson.autoconfigure.JacksonAutoConfiguration;
import org.springframework.boot.kafka.autoconfigure.KafkaAutoConfiguration;
import org.springframework.boot.mongodb.autoconfigure.MongoAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnectionAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;

@SpringBootTest(
        classes = {KafkaConfig.class, CongestionResultConsumer.class, CongestionResultService.class,
                SpotRepository.class, CongestionHistoryRepository.class, CongestionStatusMapper.class,
                CongestionWindowCalculator.class, KafkaTestContainerConfig.class, MongoTestContainerConfig.class,
                CongestionResultConsumerTest.ListenerConfig.class},
        properties = "spring.kafka.consumer.auto-offset-reset=earliest")
@ImportAutoConfiguration({
        JacksonAutoConfiguration.class,
        KafkaAutoConfiguration.class,
        MongoAutoConfiguration.class,
        DataMongoAutoConfiguration.class,
        ServiceConnectionAutoConfiguration.class})
class CongestionResultConsumerTest {

    private static final String DLT = CongestionMessages.RESULT_TOPIC + "-dlt";
    private static final Long PANGYO = 101L;
    private static final Long JEONGJA = 102L;
    private static final Long SEOHYEON = 103L;

    @Autowired
    private KafkaTemplate<String, String> kafkaTemplate;

    @Autowired
    private MongoTemplate mongoTemplate;

    @Autowired
    private BlockingQueue<ConsumerRecord<String, String>> deadLetters;

    @BeforeEach
    void setUp() {
        mongoTemplate.dropCollection("spots");
        mongoTemplate.getCollection("spots").insertMany(List.of(
                new Document("_id", PANGYO).append("type", "STATION").append("name", "판교역"),
                new Document("_id", JEONGJA).append("type", "STATION").append("name", "정자역"),
                new Document("_id", SEOHYEON).append("type", "STATION").append("name", "서현역")));
    }

    @Test
    @DisplayName("분석 완료 메시지를 받으면 스팟 최신값을 바꾼다")
    void appliesCompletedResult() throws Exception {
        send(PANGYO, 1, """
                {"spot_id":101,"window_end":"2026-11-02T07:10:00+09:00","analysis_status":"SUCCESS",
                 "result":{"congestion_level":"MEDIUM","report_count":3,
                           "causes":[{"cause_code":"RUSH_HOUR","report_count":3}],"summary":"출근 인파로 혼잡"},
                 "analyzed_at":"2026-11-02T07:11:00+09:00"}
                """);

        Spot spot = awaitCalculated(PANGYO);
        assertThat(spot.getStatus()).isEqualTo(CongestionStatus.CONGESTED);
        assertThat(spot.getCalculatedAt()).isEqualTo(Instant.parse("2026-11-01T22:10:00Z"));
    }

    @Test
    @DisplayName("읽지 못하는 버전은 반영하지 않고 바로 DLT 로 보낸다")
    void sendsUnsupportedVersionToDeadLetter() throws Exception {
        String eventId = send(JEONGJA, 2, """
                {"spot_id":102,"window_end":"2026-11-02T07:10:00+09:00","analysis_status":"FAILED",
                 "result":null,"analyzed_at":"2026-11-02T07:11:00+09:00"}
                """);

        awaitDeadLetter(eventId);
        assertThat(mongoTemplate.findById(JEONGJA, Spot.class).getCalculatedAt()).isNull();
    }

    @Test
    @DisplayName("성공인데 결과가 없는 메시지는 반영하지 않고 바로 DLT 로 보낸다")
    void sendsSuccessWithoutResultToDeadLetter() throws Exception {
        String eventId = send(SEOHYEON, 1, """
                {"spot_id":103,"window_end":"2026-11-02T07:10:00+09:00","analysis_status":"SUCCESS",
                 "result":null,"analyzed_at":"2026-11-02T07:11:00+09:00"}
                """);

        awaitDeadLetter(eventId);
        assertThat(mongoTemplate.findById(SEOHYEON, Spot.class).getCalculatedAt()).isNull();
    }

    private String send(Long spotId, int schemaVersion, String data) throws Exception {
        String eventId = UUID.randomUUID().toString();
        String value = """
                {"event_id":"%s","event_type":"%s","schema_version":%d,
                 "occurred_at":"2026-11-02T07:11:00.000+09:00","data":%s}
                """.formatted(eventId, CongestionMessages.ANALYSIS_COMPLETED, schemaVersion, data.strip());
        kafkaTemplate.send(CongestionMessages.RESULT_TOPIC, String.valueOf(spotId), value).get(10, TimeUnit.SECONDS);
        return eventId;
    }

    private Spot awaitCalculated(Long spotId) throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(60);
        while (System.nanoTime() < deadline) {
            Spot spot = mongoTemplate.findById(spotId, Spot.class);
            if (spot.getCalculatedAt() != null) {
                return spot;
            }
            Thread.sleep(200);
        }
        throw new AssertionError("spot_id=" + spotId + " 에 결과가 반영되지 않았습니다");
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
        CongestionStatusProperties congestionStatusProperties() {
            return new CongestionStatusProperties(3, 3);
        }

        @Bean
        CongestionWindowProperties congestionWindowProperties() {
            return new CongestionWindowProperties(7, 10, 17, 20);
        }

        @Bean
        NewTopic resultTopic() {
            return new NewTopic(CongestionMessages.RESULT_TOPIC, 1, (short) 1);
        }

        @Bean
        NewTopic resultDeadLetterTopic() {
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

        @KafkaListener(topics = DLT, groupId = "congestion-result-dlt-test")
        void listen(ConsumerRecord<String, String> record) {
            deadLetters.add(record);
        }
    }
}
