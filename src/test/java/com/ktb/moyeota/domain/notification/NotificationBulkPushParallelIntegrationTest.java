package com.ktb.moyeota.domain.notification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.ktb.moyeota.domain.notification.consumer.NotificationBulkPushConsumer;
import com.ktb.moyeota.domain.notification.consumer.NotificationBulkPushParallelConsumer;
import com.ktb.moyeota.domain.notification.consumer.NotificationBulkPushParallelProperties;
import com.ktb.moyeota.domain.notification.entity.NotificationPushJob;
import com.ktb.moyeota.domain.notification.entity.NotificationType;
import com.ktb.moyeota.domain.notification.entity.PushJobStatus;
import com.ktb.moyeota.domain.notification.message.NotificationPushChannel;
import com.ktb.moyeota.domain.notification.message.NotificationPushMessages;
import com.ktb.moyeota.domain.notification.service.NotificationPushBatchService;
import com.ktb.moyeota.domain.notification.service.NotificationPushPublisher;
import com.ktb.moyeota.domain.notification.push.TokenSendOutcome;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.IntStream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;

@TestPropertySource(properties = {
        "moyeota.notification.push.kafka.bulk-consumer.type=parallel",
        "moyeota.notification.push.kafka.bulk-parallel.concurrency=24"})
@Import({NotificationBulkPushParallelConsumer.class, NotificationPushBatchService.class})
@EnableConfigurationProperties(NotificationBulkPushParallelProperties.class)
class NotificationBulkPushParallelIntegrationTest extends NotificationPushKafkaIntegrationSupport {

    private static final int MAX_ATTEMPTS = 5;
    private static final int PARTITIONS = 6;

    @Autowired
    private NotificationPushPublisher publisher;

    @Autowired
    private ObjectProvider<NotificationBulkPushConsumer> listenerConsumer;

    @Test
    @DisplayName("병렬 컨슈머를 켜면 리스너 컨슈머는 뜨지 않는다")
    void listenerConsumerIsOff() {
        assertThat(listenerConsumer.getIfAvailable()).isNull();
    }

    @Test
    @DisplayName("파티션 수보다 많은 발송을 동시에 처리한다")
    void sendsMoreThanPartitionsAtOnce() {
        pushSender.delay(Duration.ofMillis(500));
        List<Long> jobIds = IntStream.range(0, 48)
                .mapToObj(i -> publishedReminder(recipientWithToken()))
                .toList();
        long startedAt = System.nanoTime();

        publisher.publish(NotificationPushChannel.BULK, jobIds);

        await().atMost(WAIT).until(() -> jobIds.stream().allMatch(id -> job(id).getStatus() == PushJobStatus.SENT));
        Duration elapsed = Duration.ofNanos(System.nanoTime() - startedAt);
        pushSender.delay(Duration.ZERO);
        Duration serialPerPartition = Duration.ofMillis(500L * jobIds.size() / PARTITIONS);
        assertThat(elapsed).isLessThan(serialPerPartition);
    }

    @Test
    @DisplayName("일시 오류가 4번 나도 마지막에 성공하면 SENT 로 끝나고 DLT 로 가지 않는다")
    void succeedsOnLastRetry() {
        Recipient recipient = recipientWithToken();
        pushSender.script(recipient.token(),
                TokenSendOutcome.TRANSIENT_FAILURE, TokenSendOutcome.TRANSIENT_FAILURE,
                TokenSendOutcome.TRANSIENT_FAILURE, TokenSendOutcome.TRANSIENT_FAILURE, TokenSendOutcome.SUCCESS);
        Long jobId = publishedReminder(recipient);

        publisher.publish(NotificationPushChannel.BULK, List.of(jobId));

        awaitStatus(jobId, PushJobStatus.SENT);
        awaitDrained(NotificationBulkPushConsumer.GROUP_ID, NotificationPushMessages.BULK_PUSH_TOPIC);
        NotificationPushJob job = job(jobId);
        assertThat(job.getAttempts()).isEqualTo(MAX_ATTEMPTS);
        assertThat(pushSender.callsOf(recipient.token())).isEqualTo(MAX_ATTEMPTS);
        assertThat(deadLetterJobIds()).doesNotContain(jobId);
    }

    @Test
    @DisplayName("5번 모두 일시 오류면 FAILED 로 기록하고 메시지를 DLT 로 보낸다")
    void sendsToDeadLetterWhenRetriesRunOut() {
        Recipient recipient = recipientWithToken();
        pushSender.script(recipient.token(),
                TokenSendOutcome.TRANSIENT_FAILURE, TokenSendOutcome.TRANSIENT_FAILURE,
                TokenSendOutcome.TRANSIENT_FAILURE, TokenSendOutcome.TRANSIENT_FAILURE,
                TokenSendOutcome.TRANSIENT_FAILURE);
        Long jobId = publishedReminder(recipient);

        publisher.publish(NotificationPushChannel.BULK, List.of(jobId));

        await().atMost(WAIT).until(() -> deadLetterJobIds().contains(jobId));
        NotificationPushJob job = job(jobId);
        assertThat(job.getStatus()).isEqualTo(PushJobStatus.FAILED);
        assertThat(job.getAttempts()).isEqualTo(MAX_ATTEMPTS);
        assertThat(pushSender.callsOf(recipient.token())).isEqualTo(MAX_ATTEMPTS);
    }

    @Test
    @DisplayName("다시 보내도 소용없는 오류면 한 번에 FAILED 로 끝내고 다시 보내지 않는다")
    void permanentFailureIsNotRetried() {
        Recipient recipient = recipientWithToken();
        pushSender.script(recipient.token(), TokenSendOutcome.PERMANENT_FAILURE);
        Long jobId = publishedReminder(recipient);

        publisher.publish(NotificationPushChannel.BULK, List.of(jobId));

        awaitStatus(jobId, PushJobStatus.FAILED);
        awaitDrained(NotificationBulkPushConsumer.GROUP_ID, NotificationPushMessages.BULK_PUSH_TOPIC);
        assertThat(job(jobId).getAttempts()).isEqualTo(1);
        assertThat(pushSender.callsOf(recipient.token())).isEqualTo(1);
        assertThat(deadLetterJobIds()).doesNotContain(jobId);
    }

    @Test
    @DisplayName("읽을 수 없는 메시지는 다시 시도하지 않고 DLT 로 보낸다")
    void unreadableMessageGoesToDeadLetter() {
        long jobId = 9_900_001L;

        kafkaTemplate.send(NotificationPushMessages.BULK_PUSH_TOPIC, "{\"data\":{\"job_id\":" + jobId + "}}");

        await().atMost(WAIT).until(() -> deadLetterJobIds().contains(jobId));
        assertThat(pushSender.callsOf(tokenOf(jobId))).isZero();
    }

    private Long publishedReminder(Recipient recipient) {
        return persistJob(recipient, NotificationType.SETTLEMENT_COMPLETION_REMINDER, PushJobStatus.PUBLISHED,
                LocalDateTime.now());
    }
}
