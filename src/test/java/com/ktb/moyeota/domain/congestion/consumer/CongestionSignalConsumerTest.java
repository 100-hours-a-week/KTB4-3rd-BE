package com.ktb.moyeota.domain.congestion.consumer;

import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;

import com.ktb.moyeota.domain.congestion.message.PostChangedMessages;
import com.ktb.moyeota.domain.congestion.service.CongestionSignalService;
import com.ktb.moyeota.global.config.KafkaConfig;
import com.ktb.moyeota.global.config.KafkaTestContainerConfig;
import java.math.BigDecimal;
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

@SpringBootTest(
        classes = {KafkaConfig.class, CongestionSignalConsumer.class, KafkaTestContainerConfig.class,
                CongestionSignalConsumerTest.ListenerConfig.class},
        properties = "spring.kafka.consumer.auto-offset-reset=earliest")
@ImportAutoConfiguration({
        JacksonAutoConfiguration.class,
        KafkaAutoConfiguration.class,
        ServiceConnectionAutoConfiguration.class})
class CongestionSignalConsumerTest {

    private static final String DLT = PostChangedMessages.TOPIC + "-dlt";

    @MockitoBean
    private CongestionSignalService signalService;

    @Autowired
    private KafkaTemplate<String, String> kafkaTemplate;

    @Autowired
    private BlockingQueue<ConsumerRecord<String, String>> deadLetters;

    @Test
    @DisplayName("글 작성 메시지를 받으면 원글 정보로 적재 서비스를 부른다")
    void loadsPostCreated() throws Exception {
        send("101", PostChangedMessages.POST_CREATED, 1, data(101L, "null"));

        verify(signalService, timeout(30_000)).load(argThat(data -> data != null
                && data.post().postId() == 101L
                && data.post().lat().compareTo(new BigDecimal("37.3948")) == 0
                && !data.isComment()));
    }

    @Test
    @DisplayName("댓글 작성 메시지는 원글과 댓글을 함께 넘긴다")
    void loadsCommentCreated() throws Exception {
        send("102", PostChangedMessages.COMMENT_CREATED, 1, data(102L,
                """
                {"comment_id":7,"author_id":11,"content":"저도 기다리는 중","created_at":"2026-11-02T10:09:00"}
                """));

        verify(signalService, timeout(30_000)).load(argThat(data -> data != null
                && data.post().postId() == 102L
                && data.isComment()
                && data.comment().commentId() == 7L));
    }

    @Test
    @DisplayName("읽지 못하는 버전은 적재하지 않고 바로 DLT 로 보낸다")
    void sendsUnsupportedVersionToDeadLetter() throws Exception {
        String eventId = send("103", PostChangedMessages.POST_CREATED, 2, data(103L, "null"));

        awaitDeadLetter(eventId);
        verify(signalService, never()).load(argThat(data -> data != null && data.post().postId() == 103L));
    }

    @Test
    @DisplayName("원글 좌표가 없는 메시지는 적재하지 않고 바로 DLT 로 보낸다")
    void sendsInvalidMessageToDeadLetter() throws Exception {
        String eventId = send("104", PostChangedMessages.POST_CREATED, 1,
                """
                {"post":{"post_id":104,"author_id":10,"created_at":"2026-11-02T10:07:00"},"comment":null}
                """);

        awaitDeadLetter(eventId);
        verify(signalService, never()).load(argThat(data -> data != null && data.post().postId() == 104L));
    }

    private static String data(long postId, String comment) {
        return """
                {"post":{"post_id":%d,"author_id":10,"title":"판교역 혼잡","content":"출구 사람 많아요",
                         "lat":37.394800,"lng":127.111200,"created_at":"2026-11-02T10:07:00"},
                 "comment":%s}
                """.formatted(postId, comment.strip());
    }

    private String send(String key, String eventType, int schemaVersion, String data) throws Exception {
        String eventId = UUID.randomUUID().toString();
        String value = """
                {"event_id":"%s","event_type":"%s","schema_version":%d,
                 "occurred_at":"2026-11-02T10:07:01.000+09:00","data":%s}
                """.formatted(eventId, eventType, schemaVersion, data.strip());
        kafkaTemplate.send(PostChangedMessages.TOPIC, key, value).get(10, TimeUnit.SECONDS);
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
        NewTopic postChangedTopic() {
            return new NewTopic(PostChangedMessages.TOPIC, 1, (short) 1);
        }

        @Bean
        NewTopic postChangedDeadLetterTopic() {
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

        @KafkaListener(topics = DLT, groupId = "congestion-signal-dlt-test")
        void listen(ConsumerRecord<String, String> record) {
            deadLetters.add(record);
        }
    }
}
