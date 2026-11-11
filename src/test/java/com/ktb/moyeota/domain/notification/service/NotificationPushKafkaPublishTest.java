package com.ktb.moyeota.domain.notification.service;

import static com.ktb.moyeota.fixture.CompanionFixture.companionPost;
import static com.ktb.moyeota.fixture.UserFixture.user;
import static org.assertj.core.api.Assertions.assertThat;

import com.ktb.moyeota.domain.chat.entity.ChatRoom;
import com.ktb.moyeota.domain.companion.entity.Companion;
import com.ktb.moyeota.domain.notification.entity.Notification;
import com.ktb.moyeota.domain.notification.entity.NotificationPushJob;
import com.ktb.moyeota.domain.notification.entity.NotificationType;
import com.ktb.moyeota.domain.notification.entity.PushJobStatus;
import com.ktb.moyeota.domain.notification.message.NotificationPushMessages;
import com.ktb.moyeota.domain.notification.message.NotificationPushTopicsTestConfig;
import com.ktb.moyeota.domain.notification.repository.NotificationPushJobRepository;
import com.ktb.moyeota.domain.notification.repository.NotificationRepository;
import com.ktb.moyeota.domain.user.entity.User;
import com.ktb.moyeota.global.config.AsyncConfig;
import com.ktb.moyeota.global.config.ClockConfig;
import com.ktb.moyeota.global.config.KafkaConfig;
import com.ktb.moyeota.global.config.KafkaTestContainerConfig;
import com.ktb.moyeota.global.config.NotificationPushConfig;
import com.ktb.moyeota.global.config.NotificationPushKafkaProperties;
import com.ktb.moyeota.global.config.NotificationPushProperties;
import com.ktb.moyeota.global.event.EventEnvelopeFactory;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jackson.autoconfigure.JacksonAutoConfiguration;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.boot.kafka.autoconfigure.KafkaAutoConfiguration;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnectionAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

@DataJpaTest(properties = {
        "moyeota.notification.push.mode=kafka",
        "moyeota.notification.push.kafka.republish-after=1m",
        "moyeota.notification.push.kafka.republish-published-after=30m",
        "spring.kafka.consumer.auto-offset-reset=earliest"})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@ImportAutoConfiguration({
        JacksonAutoConfiguration.class,
        KafkaAutoConfiguration.class,
        ServiceConnectionAutoConfiguration.class})
@Import({NotificationCommandService.class, NotificationPushPublisher.class, NotificationPushKafkaEventListener.class,
        NotificationPushRepublisher.class, NotificationPushConfig.class, AsyncConfig.class, KafkaConfig.class,
        ClockConfig.class, EventEnvelopeFactory.class, KafkaTestContainerConfig.class,
        NotificationPushTopicsTestConfig.class, NotificationPushKafkaPublishTest.TestConfig.class})
class NotificationPushKafkaPublishTest {

    private static final Duration WAIT = Duration.ofSeconds(20);
    private static final AtomicInteger USER_SEQ = new AtomicInteger();

    @TestConfiguration
    @EnableConfigurationProperties({NotificationPushProperties.class, NotificationPushKafkaProperties.class})
    static class TestConfig {

        @Bean
        BlockingQueue<ConsumerRecord<String, String>> received() {
            return new LinkedBlockingQueue<>();
        }

        @KafkaListener(
                topics = {NotificationPushMessages.PUSH_TOPIC, NotificationPushMessages.BULK_PUSH_TOPIC},
                groupId = "notification-push-publish-test")
        void listen(ConsumerRecord<String, String> record) {
            received().add(record);
        }
    }

    @Autowired
    private NotificationCommandService commandService;

    @Autowired
    private NotificationPushRepublisher republisher;

    @Autowired
    private NotificationPushJobRepository jobRepository;

    @Autowired
    private NotificationRepository notificationRepository;

    @Autowired
    private TestEntityManager entityManager;

    @Autowired
    private JsonMapper jsonMapper;

    @Autowired
    private BlockingQueue<ConsumerRecord<String, String>> received;

    @Autowired
    private PlatformTransactionManager transactionManager;

    private TransactionTemplate tx;
    private User recipient;
    private ChatRoom chatRoom;
    private long nextEventId = 1L;

    @BeforeEach
    void setUp() {
        tx = new TransactionTemplate(transactionManager);
        tx.executeWithoutResult(status -> {
            recipient = entityManager.persist(user("수신자" + USER_SEQ.incrementAndGet()));
            Companion companion = entityManager.persist(companionPost(recipient));
            chatRoom = entityManager.persist(ChatRoom.create(companion));
        });
    }

    @AfterEach
    void tearDown() {
        jobRepository.deleteAll();
        notificationRepository.deleteAll();
    }

    @Test
    @DisplayName("알림을 저장하면 커밋 뒤 job id 를 개별 토픽에 키 없이 발행하고 ack 를 받으면 PUBLISHED 로 바꾼다")
    void publishesAfterCommit() throws Exception {
        Notification notification = commandService.createForChatRoom(
                recipient, NotificationType.MATCHING_COMPLETED, nextEventId++, chatRoom, "매칭이 완료됐어요");
        Long jobId = jobRepository.findByNotificationId(notification.getId()).orElseThrow().getId();

        Map<Long, ConsumerRecord<String, String>> records = awaitRecords(Set.of(jobId));

        ConsumerRecord<String, String> record = records.get(jobId);
        assertThat(record.topic()).isEqualTo(NotificationPushMessages.PUSH_TOPIC);
        assertThat(record.key()).isNull();
        JsonNode json = jsonMapper.readTree(record.value());
        assertThat(json.get("event_type").asString()).isEqualTo(NotificationPushMessages.PUSH_REQUESTED);
        assertThat(json.get("schema_version").asInt()).isEqualTo(NotificationPushMessages.SCHEMA_VERSION);
        assertThat(awaitStatus(jobId, PushJobStatus.PUBLISHED)).isEqualTo(PushJobStatus.PUBLISHED);
    }

    @Test
    @DisplayName("스윕은 유예 시간이 지난 PENDING 과 lease 가 끝난 SENDING 만 다시 발행하고 리마인드는 대량 토픽으로 보낸다")
    void republishesOnlyStaleJobs() throws Exception {
        LocalDateTime now = LocalDateTime.now().truncatedTo(ChronoUnit.MICROS);
        LocalDateTime stale = now.minusMinutes(10);
        Long stalePending = persistJob(NotificationType.MATCHING_COMPLETED, PushJobStatus.PENDING, stale);
        Long expiredSending = persistJob(NotificationType.SETTLEMENT_REQUESTED, PushJobStatus.SENDING, stale);
        Long staleReminder = persistJob(NotificationType.SETTLEMENT_COMPLETION_REMINDER, PushJobStatus.PENDING, stale);
        Long freshPending = persistJob(NotificationType.MATCHING_COMPLETED, PushJobStatus.PENDING, now);
        Long published = persistJob(NotificationType.MATCHING_COMPLETED, PushJobStatus.PUBLISHED, stale);

        int republished = republisher.republishDue();

        assertThat(republished).isEqualTo(3);
        Map<Long, ConsumerRecord<String, String>> records =
                awaitRecords(Set.of(stalePending, expiredSending, staleReminder));
        assertThat(records.get(stalePending).topic()).isEqualTo(NotificationPushMessages.PUSH_TOPIC);
        assertThat(records.get(expiredSending).topic()).isEqualTo(NotificationPushMessages.PUSH_TOPIC);
        assertThat(records.get(staleReminder).topic()).isEqualTo(NotificationPushMessages.BULK_PUSH_TOPIC);
        assertThat(jsonMapper.readTree(records.get(staleReminder).value()).get("event_type").asString())
                .isEqualTo(NotificationPushMessages.BULK_PUSH_REQUESTED);
        assertThat(awaitStatus(expiredSending, PushJobStatus.PUBLISHED)).isEqualTo(PushJobStatus.PUBLISHED);
        assertThat(jobRepository.findById(freshPending).orElseThrow().getStatus()).isEqualTo(PushJobStatus.PENDING);
        assertThat(jobRepository.findById(published).orElseThrow().getStatus()).isEqualTo(PushJobStatus.PUBLISHED);
    }

    @Test
    @DisplayName("다시 발행한 작업은 시각을 지금으로 미뤄 다음 스윕이 바로 또 집지 않는다")
    void republishedJobWaitsAgain() {
        Long stalePending = persistJob(NotificationType.MATCHING_COMPLETED, PushJobStatus.PENDING,
                LocalDateTime.now().minusMinutes(10));

        int first = republisher.republishDue();
        int second = republisher.republishDue();

        assertThat(first).isEqualTo(1);
        assertThat(second).isZero();
        assertThat(jobRepository.findById(stalePending).orElseThrow().getNextAttemptAt())
                .isAfter(LocalDateTime.now().minusMinutes(1));
    }

    private Long persistJob(NotificationType type, PushJobStatus status, LocalDateTime nextAttemptAt) {
        return tx.execute(s -> {
            Notification notification = entityManager.persist(Notification.createForChatRoom(
                    recipient, type, nextEventId++, chatRoom, "알림"));
            NotificationPushJob job = NotificationPushJob.create(notification, nextAttemptAt);
            ReflectionTestUtils.setField(job, "status", status);
            return entityManager.persist(job).getId();
        });
    }

    private Map<Long, ConsumerRecord<String, String>> awaitRecords(Set<Long> jobIds) throws Exception {
        Map<Long, ConsumerRecord<String, String>> found = new HashMap<>();
        long deadline = System.nanoTime() + WAIT.toNanos();
        while (found.size() < jobIds.size() && System.nanoTime() < deadline) {
            ConsumerRecord<String, String> record = received.poll(1, TimeUnit.SECONDS);
            if (record == null) {
                continue;
            }
            long jobId = jsonMapper.readTree(record.value()).get("data").get("job_id").asLong();
            if (jobIds.contains(jobId)) {
                found.put(jobId, record);
            }
        }
        assertThat(found.keySet()).containsExactlyInAnyOrderElementsOf(jobIds);
        return found;
    }

    private PushJobStatus awaitStatus(Long jobId, PushJobStatus expected) throws InterruptedException {
        long deadline = System.nanoTime() + WAIT.toNanos();
        PushJobStatus status = null;
        while (System.nanoTime() < deadline) {
            status = jobRepository.findById(jobId).orElseThrow().getStatus();
            if (status == expected) {
                return status;
            }
            Thread.sleep(100);
        }
        return status;
    }
}
