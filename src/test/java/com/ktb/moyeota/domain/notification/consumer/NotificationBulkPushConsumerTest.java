package com.ktb.moyeota.domain.notification.consumer;

import static org.assertj.core.api.Assertions.assertThat;

import com.ktb.moyeota.domain.notification.message.NotificationPushMessages;
import com.ktb.moyeota.domain.notification.message.NotificationPushRequestData;
import com.ktb.moyeota.domain.notification.message.NotificationPushTopicsTestConfig;
import com.ktb.moyeota.domain.notification.service.NotificationPushService;
import com.ktb.moyeota.domain.notification.service.PushDeliveryOutcome;
import com.ktb.moyeota.global.config.KafkaConfig;
import com.ktb.moyeota.global.config.KafkaTestContainerConfig;
import com.ktb.moyeota.global.config.NotificationPushKafkaProperties;
import com.ktb.moyeota.global.event.EventEnvelope;
import java.time.OffsetDateTime;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.UUID;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.BooleanSupplier;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.jackson.autoconfigure.JacksonAutoConfiguration;
import org.springframework.boot.kafka.autoconfigure.KafkaAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnectionAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.config.KafkaListenerEndpointRegistry;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.ConcurrentMessageListenerContainer;
import tools.jackson.databind.json.JsonMapper;

@SpringBootTest(
        classes = {KafkaConfig.class, NotificationBulkPushConsumerConfig.class, NotificationBulkPushConsumer.class,
                KafkaTestContainerConfig.class, NotificationPushTopicsTestConfig.class,
                NotificationBulkPushConsumerTest.TestConfig.class},
        properties = {
                "spring.kafka.consumer.auto-offset-reset=earliest",
                "moyeota.notification.push.mode=kafka",
                "moyeota.notification.push.kafka.bulk-retry-initial-interval=200ms"})
@ImportAutoConfiguration({
        JacksonAutoConfiguration.class,
        KafkaAutoConfiguration.class,
        ServiceConnectionAutoConfiguration.class})
class NotificationBulkPushConsumerTest {

    private static final AtomicLong JOB_SEQ = new AtomicLong(800_000L);
    private static final int MAX_ATTEMPTS = NotificationBulkPushConsumerConfig.MAX_ATTEMPTS;

    @Autowired
    private RecordingPushService pushService;

    @Autowired
    private KafkaTemplate<String, String> kafkaTemplate;

    @Autowired
    private JsonMapper jsonMapper;

    @Autowired
    private KafkaListenerEndpointRegistry listenerRegistry;

    @Autowired
    private BlockingQueue<ConsumerRecord<String, String>> deadLetters;

    @Test
    @DisplayName("요청을 받으면 job id 와 대량 발송 최대 시도 횟수(5)로 멱등 발송을 부른다")
    void delivers() throws Exception {
        long jobId = JOB_SEQ.incrementAndGet();

        send(NotificationPushMessages.BULK_PUSH_REQUESTED, NotificationPushMessages.SCHEMA_VERSION, jobId);

        await(() -> pushService.callsOf(jobId) == 1);
        assertThat(pushService.maxAttemptsOf(jobId)).containsExactly(MAX_ATTEMPTS);
    }

    @Test
    @DisplayName("이미 처리된 작업이면 다시 시도하지 않는다")
    void notClaimedIsNotRetried() throws Exception {
        long jobId = JOB_SEQ.incrementAndGet();
        pushService.willReturn(jobId, PushDeliveryOutcome.NOT_CLAIMED);

        send(NotificationPushMessages.BULK_PUSH_REQUESTED, NotificationPushMessages.SCHEMA_VERSION, jobId);

        await(() -> pushService.callsOf(jobId) == 1);
        Thread.sleep(2_000);
        assertThat(pushService.callsOf(jobId)).isEqualTo(1);
    }

    @Test
    @DisplayName("일시 오류면 간격을 늘려 가며 4번까지 다시 보내고, 마지막에 성공하면 DLT 로 보내지 않는다")
    void retriesFourTimes() throws Exception {
        long jobId = JOB_SEQ.incrementAndGet();
        pushService.willReturn(jobId,
                PushDeliveryOutcome.RETRY_SCHEDULED, PushDeliveryOutcome.RETRY_SCHEDULED,
                PushDeliveryOutcome.RETRY_SCHEDULED, PushDeliveryOutcome.RETRY_SCHEDULED, PushDeliveryOutcome.SENT);

        String eventId = send(NotificationPushMessages.BULK_PUSH_REQUESTED, NotificationPushMessages.SCHEMA_VERSION,
                jobId);

        await(() -> pushService.callsOf(jobId) == MAX_ATTEMPTS);
        assertNoDeadLetter(eventId);
    }

    @Test
    @DisplayName("4번 다시 보내도 일시 오류면 DLT 로 보낸다")
    void sendsToDeadLetterAfterRetries() throws Exception {
        long jobId = JOB_SEQ.incrementAndGet();
        pushService.willReturn(jobId,
                PushDeliveryOutcome.RETRY_SCHEDULED, PushDeliveryOutcome.RETRY_SCHEDULED,
                PushDeliveryOutcome.RETRY_SCHEDULED, PushDeliveryOutcome.RETRY_SCHEDULED,
                PushDeliveryOutcome.RETRIES_EXHAUSTED);

        String eventId = send(NotificationPushMessages.BULK_PUSH_REQUESTED, NotificationPushMessages.SCHEMA_VERSION,
                jobId);

        awaitDeadLetter(eventId);
        assertThat(pushService.callsOf(jobId)).isEqualTo(MAX_ATTEMPTS);
    }

    @Test
    @DisplayName("읽지 못하는 버전은 보내지 않고 바로 DLT 로 보낸다")
    void sendsUnsupportedVersionToDeadLetter() throws Exception {
        long jobId = JOB_SEQ.incrementAndGet();

        String eventId = send(NotificationPushMessages.BULK_PUSH_REQUESTED, 2, jobId);

        awaitDeadLetter(eventId);
        assertThat(pushService.callsOf(jobId)).isZero();
    }

    @Test
    @DisplayName("모르는 이벤트 종류는 보내지 않고 넘긴다")
    void skipsUnknownEventType() throws Exception {
        long skipped = JOB_SEQ.incrementAndGet();
        long delivered = JOB_SEQ.incrementAndGet();

        send("notification.bulk-push.unknown", NotificationPushMessages.SCHEMA_VERSION, skipped);
        send(NotificationPushMessages.BULK_PUSH_REQUESTED, NotificationPushMessages.SCHEMA_VERSION, delivered);

        await(() -> pushService.callsOf(delivered) == 1);
        Thread.sleep(2_000);
        assertThat(pushService.callsOf(skipped)).isZero();
    }

    @Test
    @DisplayName("대량 발송 컨슈머는 요청 토픽 파티션 수(6)만큼 동시에 처리한다")
    void concurrencyMatchesPartitions() {
        ConcurrentMessageListenerContainer<?, ?> container = listenerRegistry.getListenerContainers().stream()
                .filter(each -> NotificationBulkPushConsumer.GROUP_ID.equals(each.getGroupId()))
                .map(ConcurrentMessageListenerContainer.class::cast)
                .findFirst()
                .orElseThrow();

        assertThat(container.getConcurrency()).isEqualTo(NotificationPushTopicsTestConfig.PARTITIONS);
    }

    private String send(String eventType, int schemaVersion, long jobId) throws Exception {
        String eventId = UUID.randomUUID().toString();
        String value = jsonMapper.writeValueAsString(new EventEnvelope<>(
                eventId, eventType, schemaVersion, OffsetDateTime.now(), new NotificationPushRequestData(jobId)));
        kafkaTemplate.send(NotificationPushMessages.BULK_PUSH_TOPIC, value).get(10, TimeUnit.SECONDS);
        return eventId;
    }

    private static void await(BooleanSupplier condition) throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(60);
        while (System.nanoTime() < deadline) {
            if (condition.getAsBoolean()) {
                return;
            }
            Thread.sleep(100);
        }
        throw new AssertionError("60초 안에 조건을 만족하지 못했습니다");
    }

    private void awaitDeadLetter(String eventId) throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(60);
        while (System.nanoTime() < deadline) {
            ConsumerRecord<String, String> record = deadLetters.poll(1, TimeUnit.SECONDS);
            if (record != null && record.value().contains(eventId)) {
                return;
            }
        }
        throw new AssertionError("DLT 에서 event_id=" + eventId + " 메시지를 받지 못했습니다");
    }

    private void assertNoDeadLetter(String eventId) throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(3);
        while (System.nanoTime() < deadline) {
            ConsumerRecord<String, String> record = deadLetters.poll(1, TimeUnit.SECONDS);
            if (record != null && record.value().contains(eventId)) {
                throw new AssertionError("DLT 로 가면 안 되는 메시지가 갔습니다: event_id=" + eventId);
            }
        }
    }

    static class RecordingPushService extends NotificationPushService {

        private final Queue<long[]> calls = new ConcurrentLinkedQueue<>();
        private final Map<Long, Deque<PushDeliveryOutcome>> outcomes = new ConcurrentHashMap<>();

        RecordingPushService() {
            super(null, null, null, null, null, null);
        }

        void willReturn(long jobId, PushDeliveryOutcome... results) {
            outcomes.put(jobId, new ArrayDeque<>(List.of(results)));
        }

        long callsOf(long jobId) {
            return calls.stream().filter(call -> call[0] == jobId).count();
        }

        List<Integer> maxAttemptsOf(long jobId) {
            return calls.stream().filter(call -> call[0] == jobId).map(call -> (int) call[1]).toList();
        }

        @Override
        public PushDeliveryOutcome deliver(Long jobId, int maxAttempts) {
            calls.add(new long[] {jobId, maxAttempts});
            Deque<PushDeliveryOutcome> scripted = outcomes.get(jobId);
            if (scripted == null || scripted.isEmpty()) {
                return PushDeliveryOutcome.SENT;
            }
            return scripted.poll();
        }
    }

    @TestConfiguration(proxyBeanMethods = false)
    @EnableConfigurationProperties(NotificationPushKafkaProperties.class)
    static class TestConfig {

        @Bean
        RecordingPushService pushService() {
            return new RecordingPushService();
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

        @KafkaListener(topics = NotificationPushTopicsTestConfig.BULK_PUSH_DLT,
                groupId = "notification-bulk-push-dlt-test")
        void listen(ConsumerRecord<String, String> record) {
            deadLetters.add(record);
        }
    }
}
