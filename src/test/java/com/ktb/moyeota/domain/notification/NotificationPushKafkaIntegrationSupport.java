package com.ktb.moyeota.domain.notification;

import static com.ktb.moyeota.fixture.CompanionFixture.companionPost;
import static com.ktb.moyeota.fixture.UserFixture.user;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;

import com.ktb.moyeota.domain.chat.entity.ChatRoom;
import com.ktb.moyeota.domain.companion.entity.Companion;
import com.ktb.moyeota.domain.notification.consumer.NotificationBulkPushConsumer;
import com.ktb.moyeota.domain.notification.consumer.NotificationBulkPushConsumerConfig;
import com.ktb.moyeota.domain.notification.consumer.NotificationPushConsumer;
import com.ktb.moyeota.domain.notification.consumer.NotificationPushConsumerConfig;
import com.ktb.moyeota.domain.notification.entity.Notification;
import com.ktb.moyeota.domain.notification.entity.NotificationPushJob;
import com.ktb.moyeota.domain.notification.entity.NotificationType;
import com.ktb.moyeota.domain.notification.entity.PushJobStatus;
import com.ktb.moyeota.domain.notification.message.NotificationPushRequestData;
import com.ktb.moyeota.domain.notification.message.NotificationPushTopicsTestConfig;
import com.ktb.moyeota.domain.notification.repository.NotificationPushJobRepository;
import com.ktb.moyeota.domain.notification.service.NotificationCommandService;
import com.ktb.moyeota.domain.notification.service.NotificationPushKafkaEventListener;
import com.ktb.moyeota.domain.notification.service.NotificationPushPublisher;
import com.ktb.moyeota.domain.notification.service.NotificationPushRepublisher;
import com.ktb.moyeota.domain.notification.service.NotificationPushService;
import com.ktb.moyeota.domain.user.entity.User;
import com.ktb.moyeota.domain.user.repository.PushTokenRepository;
import com.ktb.moyeota.global.config.AsyncConfig;
import com.ktb.moyeota.global.config.ClockConfig;
import com.ktb.moyeota.global.config.KafkaConfig;
import com.ktb.moyeota.global.config.NotificationPushConfig;
import com.ktb.moyeota.global.config.NotificationPushKafkaProperties;
import com.ktb.moyeota.global.config.NotificationPushProperties;
import com.ktb.moyeota.global.event.EventEnvelopeFactory;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;
import org.apache.kafka.clients.admin.Admin;
import org.apache.kafka.clients.admin.AdminClientConfig;
import org.apache.kafka.clients.admin.OffsetSpec;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.OffsetAndMetadata;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.TopicPartition;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jackson.autoconfigure.JacksonAutoConfiguration;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.boot.kafka.autoconfigure.KafkaAutoConfiguration;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.kafka.KafkaContainer;
import org.testcontainers.utility.DockerImageName;
import tools.jackson.databind.json.JsonMapper;

@DataJpaTest(properties = {
        "moyeota.notification.push.mode=kafka",
        "moyeota.notification.push.enabled=true",
        "moyeota.notification.push.kafka.republish-after=1s",
        "moyeota.notification.push.kafka.bulk-retry-initial-interval=200ms",
        "spring.kafka.listener.concurrency=3",
        "spring.kafka.consumer.auto-offset-reset=earliest",
        "spring.kafka.producer.properties.request.timeout.ms=1000",
        "spring.kafka.producer.properties.delivery.timeout.ms=3000",
        "spring.kafka.producer.properties.max.block.ms=2000"})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@ImportAutoConfiguration({JacksonAutoConfiguration.class, KafkaAutoConfiguration.class})
@Import({NotificationCommandService.class, NotificationPushPublisher.class, NotificationPushKafkaEventListener.class,
        NotificationPushRepublisher.class, NotificationPushService.class,
        NotificationPushConsumer.class, NotificationPushConsumerConfig.class,
        NotificationBulkPushConsumer.class, NotificationBulkPushConsumerConfig.class,
        NotificationPushConfig.class, AsyncConfig.class, KafkaConfig.class, ClockConfig.class,
        EventEnvelopeFactory.class, NotificationPushTopicsTestConfig.class,
        NotificationPushKafkaIntegrationSupport.TestConfig.class})
abstract class NotificationPushKafkaIntegrationSupport {

    static final Duration WAIT = Duration.ofSeconds(30);
    static final KafkaContainer KAFKA = new KafkaContainer(DockerImageName.parse("apache/kafka:4.2.0"));
    private static final AtomicInteger SEQ = new AtomicInteger();

    static {
        KAFKA.start();
    }

    @DynamicPropertySource
    static void kafka(DynamicPropertyRegistry registry) {
        registry.add("spring.kafka.bootstrap-servers", KAFKA::getBootstrapServers);
    }

    @TestConfiguration
    @EnableConfigurationProperties({NotificationPushProperties.class, NotificationPushKafkaProperties.class})
    static class TestConfig {

        @Bean
        ScriptedPushSender pushSender() {
            return new ScriptedPushSender();
        }

        @Bean
        Queue<ConsumerRecord<String, String>> deadLetters() {
            return new ConcurrentLinkedQueue<>();
        }

        @KafkaListener(
                topics = {NotificationPushTopicsTestConfig.PUSH_DLT, NotificationPushTopicsTestConfig.BULK_PUSH_DLT},
                groupId = "notification-push-integration-dlt")
        void collectDeadLetter(ConsumerRecord<String, String> record) {
            deadLetters().add(record);
        }
    }

    @Autowired
    ScriptedPushSender pushSender;

    @Autowired
    NotificationPushJobRepository jobRepository;

    @Autowired
    Queue<ConsumerRecord<String, String>> deadLetters;

    @Autowired
    TestEntityManager entityManager;

    @Autowired
    JsonMapper jsonMapper;

    @Autowired
    PlatformTransactionManager transactionManager;

    @Autowired
    KafkaTemplate<String, String> kafkaTemplate;

    @Autowired
    EventEnvelopeFactory envelopeFactory;

    @MockitoBean
    PushTokenRepository pushTokenRepository;

    @BeforeEach
    void tokenPerUser() {
        given(pushTokenRepository.findTokensByUserId(anyLong()))
                .willAnswer(invocation -> List.of(tokenOf(invocation.getArgument(0))));
    }

    record Recipient(User user, ChatRoom chatRoom, String token) {
    }

    Recipient recipientWithToken() {
        int seq = SEQ.incrementAndGet();
        return new TransactionTemplate(transactionManager).execute(status -> {
            User user = entityManager.persist(user("수신자" + seq));
            ChatRoom chatRoom = entityManager.persist(ChatRoom.create(entityManager.persist(companionPost(user))));
            return new Recipient(user, chatRoom, tokenOf(user.getId()));
        });
    }

    static String tokenOf(Long userId) {
        return "token-" + userId;
    }

    Long persistJob(Recipient recipient, NotificationType type, PushJobStatus status, LocalDateTime nextAttemptAt) {
        return new TransactionTemplate(transactionManager).execute(s -> {
            Notification notification = entityManager.persist(Notification.createForChatRoom(
                    recipient.user(), type, (long) SEQ.incrementAndGet(), recipient.chatRoom(), "알림"));
            NotificationPushJob job = NotificationPushJob.create(notification, nextAttemptAt);
            ReflectionTestUtils.setField(job, "status", status);
            return entityManager.persist(job).getId();
        });
    }

    NotificationPushJob job(Long jobId) {
        return jobRepository.findById(jobId).orElseThrow();
    }

    static LocalDateTime minutesAgo(long minutes) {
        return LocalDateTime.now().truncatedTo(ChronoUnit.MICROS).minusMinutes(minutes);
    }

    void awaitStatus(Long jobId, PushJobStatus expected) {
        await().atMost(WAIT).until(() -> job(jobId).getStatus() == expected);
    }

    void awaitDrained(String groupId, String topic) {
        try (Admin admin = Admin.create(Map.of(AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG, KAFKA.getBootstrapServers()))) {
            await().atMost(WAIT).until(() -> lag(admin, groupId, topic) == 0);
        }
    }

    void sendRequest(String topic, String eventType, int schemaVersion, Integer partition, Long jobId) {
        String value = jsonMapper.writeValueAsString(
                envelopeFactory.create(eventType, schemaVersion, new NotificationPushRequestData(jobId)));
        kafkaTemplate.send(new ProducerRecord<>(topic, partition, null, value));
    }

    List<Long> deadLetterJobIds() {
        return deadLetters.stream()
                .map(record -> jsonMapper.readTree(record.value()).get("data").get("job_id").asLong())
                .toList();
    }

    private static long lag(Admin admin, String groupId, String topic) throws Exception {
        Map<TopicPartition, OffsetAndMetadata> committed =
                admin.listConsumerGroupOffsets(groupId).partitionsToOffsetAndMetadata().get();
        Map<TopicPartition, OffsetSpec> latest = new ConcurrentHashMap<>();
        IntStream.range(0, NotificationPushTopicsTestConfig.PARTITIONS)
                .forEach(partition -> latest.put(new TopicPartition(topic, partition), OffsetSpec.latest()));
        return admin.listOffsets(latest).all().get().entrySet().stream()
                .mapToLong(end -> {
                    OffsetAndMetadata done = committed.get(end.getKey());
                    return end.getValue().offset() - (done == null ? 0 : done.offset());
                })
                .sum();
    }
}
