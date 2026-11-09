package com.ktb.moyeota.domain.settlement.consumer;

import static org.assertj.core.api.Assertions.assertThat;

import com.ktb.moyeota.domain.settlement.message.ImageAnalysisMessages;
import com.ktb.moyeota.domain.settlement.service.MeterAnalysisService;
import com.ktb.moyeota.global.config.KafkaConfig;
import com.ktb.moyeota.global.config.KafkaTestContainerConfig;
import com.ktb.moyeota.global.config.TestClockConfig;
import com.ktb.moyeota.global.external.ai.AnalysisOutcome;
import java.time.LocalDateTime;
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
        classes = {KafkaConfig.class, ImageAnalysisResultConsumer.class, KafkaTestContainerConfig.class,
                TestClockConfig.class, ImageAnalysisResultConsumerTest.ListenerConfig.class},
        properties = {
                "spring.kafka.consumer.auto-offset-reset=earliest",
                "moyeota.settlement.meter-analysis.mode=kafka"})
@ImportAutoConfiguration({
        JacksonAutoConfiguration.class,
        KafkaAutoConfiguration.class,
        ServiceConnectionAutoConfiguration.class})
class ImageAnalysisResultConsumerTest {

    private static final String DLT = ImageAnalysisMessages.RESULT_TOPIC + "-dlt";
    private static final LocalDateTime STARTED_AT = LocalDateTime.of(2026, 11, 9, 14, 30, 5);

    @Autowired
    private KafkaTemplate<String, String> kafkaTemplate;

    @Autowired
    private BlockingQueue<Applied> applied;

    @Autowired
    private BlockingQueue<ConsumerRecord<String, String>> deadLetters;

    @Test
    @DisplayName("미터기 읽음 결과는 분석 시작 시각을 서버 시간대로 바꿔 금액과 함께 반영한다")
    void appliesRead() throws Exception {
        send(101L, ImageAnalysisMessages.METER_COMPLETED, 1, """
                {"settlement_id":101,"analysis_started_at":"2026-11-09T05:30:05Z","analysis_status":"READ",
                 "error_code":null,"cost":18000}
                """);

        assertThat(awaitApplied(101L)).isEqualTo(new Applied(101L, STARTED_AT, new AnalysisOutcome.Read<>(18000)));
    }

    @Test
    @DisplayName("사진 문제 · 시스템 문제 결과는 오류 코드를 그대로 넘긴다")
    void appliesFailures() throws Exception {
        send(102L, ImageAnalysisMessages.METER_COMPLETED, 1, """
                {"settlement_id":102,"analysis_started_at":"2026-11-09T14:30:05+09:00",
                 "analysis_status":"UNREADABLE","error_code":"JUDGE_PARSE_FAILED","cost":null}
                """);
        send(103L, ImageAnalysisMessages.METER_COMPLETED, 1, """
                {"settlement_id":103,"analysis_started_at":"2026-11-09T14:30:05+09:00",
                 "analysis_status":"SYSTEM_FAILURE","error_code":"QUEUE_WAIT_EXCEEDED","cost":null}
                """);

        assertThat(awaitApplied(102L).outcome()).isEqualTo(new AnalysisOutcome.Unreadable<>("JUDGE_PARSE_FAILED"));
        assertThat(awaitApplied(103L).outcome()).isEqualTo(new AnalysisOutcome.SystemFailure<>("QUEUE_WAIT_EXCEEDED"));
    }

    @Test
    @DisplayName("송금 결과처럼 미터기가 아닌 결과는 버전과 상관없이 건너뛰고 DLT 로 보내지 않는다")
    void skipsOtherEvents() throws Exception {
        send(104L, "receipt.analysis.completed", 2, """
                {"settlement_id":104,"user_id":7,"analysis_status":"READ"}
                """);
        send(105L, ImageAnalysisMessages.METER_COMPLETED, 1, """
                {"settlement_id":105,"analysis_started_at":"2026-11-09T14:30:05+09:00","analysis_status":"READ",
                 "error_code":null,"cost":5000}
                """);

        assertThat(awaitApplied(105L).settlementId()).isEqualTo(105L);
        assertThat(applied).noneMatch(a -> a.settlementId().equals(104L));
        assertThat(deadLetters).noneMatch(record -> record.key().startsWith("104:"));
    }

    @Test
    @DisplayName("읽음인데 금액이 없으면 반영하지 않고 바로 DLT 로 보낸다")
    void sendsReadWithoutCostToDeadLetter() throws Exception {
        String eventId = send(106L, ImageAnalysisMessages.METER_COMPLETED, 1, """
                {"settlement_id":106,"analysis_started_at":"2026-11-09T14:30:05+09:00","analysis_status":"READ",
                 "error_code":null,"cost":null}
                """);

        assertThat(awaitDeadLetter(eventId).key()).isEqualTo("106:9");
        assertThat(applied).noneMatch(a -> a.settlementId().equals(106L));
    }

    @Test
    @DisplayName("읽지 못하는 버전은 반영하지 않고 바로 DLT 로 보낸다")
    void sendsUnsupportedVersionToDeadLetter() throws Exception {
        String eventId = send(107L, ImageAnalysisMessages.METER_COMPLETED, 2, """
                {"settlement_id":107,"analysis_started_at":"2026-11-09T14:30:05+09:00","analysis_status":"READ",
                 "error_code":null,"cost":5000}
                """);

        awaitDeadLetter(eventId);
        assertThat(applied).noneMatch(a -> a.settlementId().equals(107L));
    }

    @Test
    @DisplayName("결과 상태가 명세에 없는 값이면 반영하지 않고 바로 DLT 로 보낸다")
    void sendsUnknownStatusToDeadLetter() throws Exception {
        String eventId = send(108L, ImageAnalysisMessages.METER_COMPLETED, 1, """
                {"settlement_id":108,"analysis_started_at":"2026-11-09T14:30:05+09:00","analysis_status":"DONE",
                 "error_code":null,"cost":5000}
                """);

        awaitDeadLetter(eventId);
        assertThat(applied).noneMatch(a -> a.settlementId().equals(108L));
    }

    private String send(Long settlementId, String eventType, int schemaVersion, String data) throws Exception {
        String eventId = UUID.randomUUID().toString();
        String value = """
                {"event_id":"%s","event_type":"%s","schema_version":%d,
                 "occurred_at":"2026-11-09T14:30:40.000+09:00","data":%s}
                """.formatted(eventId, eventType, schemaVersion, data.strip());
        kafkaTemplate.send(ImageAnalysisMessages.RESULT_TOPIC, settlementId + ":9", value).get(10, TimeUnit.SECONDS);
        return eventId;
    }

    private Applied awaitApplied(Long settlementId) throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(60);
        while (System.nanoTime() < deadline) {
            for (Applied each : applied) {
                if (each.settlementId().equals(settlementId)) {
                    return each;
                }
            }
            Thread.sleep(200);
        }
        throw new AssertionError("settlement_id=" + settlementId + " 결과가 반영되지 않았습니다");
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

    record Applied(Long settlementId, LocalDateTime startedAt, AnalysisOutcome<Integer> outcome) {
    }

    static class RecordingMeterAnalysisService extends MeterAnalysisService {

        private final BlockingQueue<Applied> applied;

        RecordingMeterAnalysisService(BlockingQueue<Applied> applied) {
            super(null, null, null, null, null, null, null);
            this.applied = applied;
        }

        @Override
        public void apply(Long settlementId, LocalDateTime startedAt, AnalysisOutcome<Integer> analyzed) {
            applied.add(new Applied(settlementId, startedAt, analyzed));
        }
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class ListenerConfig {

        @Bean
        NewTopic imageAnalysisResultTopic() {
            return new NewTopic(ImageAnalysisMessages.RESULT_TOPIC, 1, (short) 1);
        }

        @Bean
        NewTopic imageAnalysisResultDeadLetterTopic() {
            return new NewTopic(DLT, 1, (short) 1);
        }

        @Bean
        BlockingQueue<Applied> applied() {
            return new LinkedBlockingQueue<>();
        }

        @Bean
        MeterAnalysisService meterAnalysisService(BlockingQueue<Applied> applied) {
            return new RecordingMeterAnalysisService(applied);
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

        @KafkaListener(topics = DLT, groupId = "image-analysis-result-dlt-test")
        void listen(ConsumerRecord<String, String> record) {
            deadLetters.add(record);
        }
    }
}
