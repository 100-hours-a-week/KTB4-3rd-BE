package com.ktb.moyeota.domain.notification.consumer;

import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.after;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.ktb.moyeota.domain.notification.message.NotificationPushMessages;
import com.ktb.moyeota.domain.notification.message.NotificationPushRequestData;
import com.ktb.moyeota.domain.notification.message.NotificationPushTopicsTestConfig;
import com.ktb.moyeota.domain.notification.service.NotificationPushService;
import com.ktb.moyeota.domain.notification.service.PushDeliveryOutcome;
import com.ktb.moyeota.global.config.KafkaConfig;
import com.ktb.moyeota.global.config.KafkaTestContainerConfig;
import com.ktb.moyeota.global.event.EventEnvelope;
import java.time.OffsetDateTime;
import java.util.UUID;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.BeforeEach;
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
        classes = {KafkaConfig.class, NotificationPushConsumerConfig.class, NotificationPushConsumer.class,
                KafkaTestContainerConfig.class, NotificationPushTopicsTestConfig.class,
                NotificationPushConsumerTest.ListenerConfig.class},
        properties = {
                "spring.kafka.consumer.auto-offset-reset=earliest",
                "moyeota.notification.push.mode=kafka"})
@ImportAutoConfiguration({
        JacksonAutoConfiguration.class,
        KafkaAutoConfiguration.class,
        ServiceConnectionAutoConfiguration.class})
class NotificationPushConsumerTest {

    private static final AtomicLong JOB_SEQ = new AtomicLong(900_000L);
    private static final int MAX_ATTEMPTS = NotificationPushConsumerConfig.MAX_ATTEMPTS;

    @MockitoBean
    private NotificationPushService pushService;

    @Autowired
    private KafkaTemplate<String, String> kafkaTemplate;

    @Autowired
    private JsonMapper jsonMapper;

    @Autowired
    private BlockingQueue<ConsumerRecord<String, String>> deadLetters;

    @BeforeEach
    void setUp() {
        given(pushService.deliver(anyLong(), anyInt())).willReturn(PushDeliveryOutcome.SENT);
    }

    @Test
    @DisplayName("요청을 받으면 job id 와 개별 발송 최대 시도 횟수(2)로 멱등 발송을 부른다")
    void delivers() throws Exception {
        long jobId = JOB_SEQ.incrementAndGet();

        send(NotificationPushMessages.PUSH_REQUESTED, NotificationPushMessages.SCHEMA_VERSION, jobId);

        verify(pushService, timeout(30_000)).deliver(jobId, MAX_ATTEMPTS);
    }

    @Test
    @DisplayName("이미 처리된 작업이면 다시 시도하지 않는다")
    void notClaimedIsNotRetried() throws Exception {
        long jobId = JOB_SEQ.incrementAndGet();
        given(pushService.deliver(jobId, MAX_ATTEMPTS)).willReturn(PushDeliveryOutcome.NOT_CLAIMED);

        send(NotificationPushMessages.PUSH_REQUESTED, NotificationPushMessages.SCHEMA_VERSION, jobId);

        verify(pushService, after(15_000).times(1)).deliver(jobId, MAX_ATTEMPTS);
    }

    @Test
    @DisplayName("일시 오류면 10초 뒤 1번 더 보내고, 그때 성공하면 DLT 로 보내지 않는다")
    void retriesOnce() throws Exception {
        long jobId = JOB_SEQ.incrementAndGet();
        given(pushService.deliver(jobId, MAX_ATTEMPTS))
                .willReturn(PushDeliveryOutcome.RETRY_SCHEDULED, PushDeliveryOutcome.SENT);

        String eventId = send(NotificationPushMessages.PUSH_REQUESTED, NotificationPushMessages.SCHEMA_VERSION, jobId);

        verify(pushService, timeout(30_000).times(2)).deliver(jobId, MAX_ATTEMPTS);
        assertNoDeadLetter(eventId);
    }

    @Test
    @DisplayName("재시도에서도 일시 오류면 DLT 로 보낸다")
    void sendsToDeadLetterAfterRetry() throws Exception {
        long jobId = JOB_SEQ.incrementAndGet();
        given(pushService.deliver(jobId, MAX_ATTEMPTS))
                .willReturn(PushDeliveryOutcome.RETRY_SCHEDULED, PushDeliveryOutcome.RETRIES_EXHAUSTED);

        String eventId = send(NotificationPushMessages.PUSH_REQUESTED, NotificationPushMessages.SCHEMA_VERSION, jobId);

        awaitDeadLetter(eventId);
        verify(pushService, times(2)).deliver(jobId, MAX_ATTEMPTS);
    }

    @Test
    @DisplayName("읽지 못하는 버전은 보내지 않고 바로 DLT 로 보낸다")
    void sendsUnsupportedVersionToDeadLetter() throws Exception {
        long jobId = JOB_SEQ.incrementAndGet();

        String eventId = send(NotificationPushMessages.PUSH_REQUESTED, 2, jobId);

        awaitDeadLetter(eventId);
        verify(pushService, never()).deliver(eq(jobId), anyInt());
    }

    @Test
    @DisplayName("모르는 이벤트 종류는 보내지 않고 넘긴다")
    void skipsUnknownEventType() throws Exception {
        long jobId = JOB_SEQ.incrementAndGet();

        send("notification.push.unknown", NotificationPushMessages.SCHEMA_VERSION, jobId);

        verify(pushService, after(5_000).never()).deliver(eq(jobId), anyInt());
    }

    private String send(String eventType, int schemaVersion, long jobId) throws Exception {
        String eventId = UUID.randomUUID().toString();
        String value = jsonMapper.writeValueAsString(new EventEnvelope<>(
                eventId, eventType, schemaVersion, OffsetDateTime.now(), new NotificationPushRequestData(jobId)));
        kafkaTemplate.send(NotificationPushMessages.PUSH_TOPIC, value).get(10, TimeUnit.SECONDS);
        return eventId;
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
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (System.nanoTime() < deadline) {
            ConsumerRecord<String, String> record = deadLetters.poll(1, TimeUnit.SECONDS);
            if (record != null && record.value().contains(eventId)) {
                throw new AssertionError("DLT 로 가면 안 되는 메시지가 갔습니다: event_id=" + eventId);
            }
        }
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class ListenerConfig {

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

        @KafkaListener(topics = NotificationPushTopicsTestConfig.PUSH_DLT, groupId = "notification-push-dlt-test")
        void listen(ConsumerRecord<String, String> record) {
            deadLetters.add(record);
        }
    }
}
