package com.ktb.moyeota.domain.notification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.ktb.moyeota.domain.notification.consumer.NotificationPushConsumer;
import com.ktb.moyeota.domain.notification.consumer.NotificationPushParallelConsumer;
import com.ktb.moyeota.domain.notification.consumer.NotificationPushParallelProperties;
import com.ktb.moyeota.domain.notification.entity.NotificationType;
import com.ktb.moyeota.domain.notification.entity.PushJobStatus;
import com.ktb.moyeota.domain.notification.message.NotificationPushMessages;
import com.ktb.moyeota.domain.notification.message.NotificationPushRequestData;
import com.ktb.moyeota.domain.notification.push.TokenSendOutcome;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.IntStream;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;

@TestPropertySource(properties = "moyeota.notification.push.kafka.push-consumer.type=parallel")
@Import(NotificationPushParallelConsumer.class)
@EnableConfigurationProperties(NotificationPushParallelProperties.class)
class NotificationPushParallelIntegrationTest extends NotificationPushKafkaIntegrationSupport {

    @Autowired
    private ObjectProvider<NotificationPushConsumer> listenerConsumer;

    @Test
    @DisplayName("병렬 컨슈머를 켜면 리스너 컨슈머는 뜨지 않는다")
    void listenerConsumerIsOff() {
        assertThat(listenerConsumer.getIfAvailable()).isNull();
    }

    @Test
    @DisplayName("같은 파티션 · 같은 사람의 알림도 차례를 기다리지 않고 동시에 보낸다")
    void sendsSameRecipientTogether() {
        pushSender.delay(Duration.ofMillis(300));
        Recipient recipient = recipientWithToken();
        List<Long> jobs = IntStream.range(0, 6).mapToObj(i -> publishedJob(recipient)).toList();
        long startedAt = System.nanoTime();

        jobs.forEach(jobId -> sendKeyed(0, recipient, jobId));

        await().atMost(WAIT).until(() -> jobs.stream().allMatch(id -> job(id).getStatus() == PushJobStatus.SENT));
        Duration elapsed = Duration.ofNanos(System.nanoTime() - startedAt);
        pushSender.delay(Duration.ZERO);
        assertThat(elapsed).isLessThan(Duration.ofMillis(300L * jobs.size() / 2));
    }

    @Test
    @DisplayName("같은 사람의 앞 알림이 일시 오류로 재시도를 기다려도 뒤 알림은 먼저 보낸다")
    void retryDoesNotBlockSameRecipient() {
        Recipient recipient = recipientWithToken();
        Long failingJob = publishedJob(recipient);
        Long nextJob = publishedJob(recipient);
        pushSender.failNotificationOnce(notificationIdOf(failingJob));

        sendKeyed(0, recipient, failingJob);
        sendKeyed(0, recipient, nextJob);

        awaitStatus(nextJob, PushJobStatus.SENT);
        assertThat(job(failingJob).getStatus()).isNotEqualTo(PushJobStatus.SENT);
        awaitStatus(failingJob, PushJobStatus.SENT);
    }

    @Test
    @DisplayName("한 사람의 알림이 일시 오류로 재시도를 기다려도 같은 파티션의 다른 사람 알림은 먼저 보낸다")
    void retryDoesNotBlockOtherRecipients() {
        Recipient failing = recipientWithToken();
        Recipient other = recipientWithToken();
        pushSender.script(failing.token(), TokenSendOutcome.TRANSIENT_FAILURE, TokenSendOutcome.SUCCESS);
        Long failingJob = publishedJob(failing);
        Long otherJob = publishedJob(other);

        sendKeyed(0, failing, failingJob);
        sendKeyed(0, other, otherJob);

        awaitStatus(otherJob, PushJobStatus.SENT);
        assertThat(job(failingJob).getStatus()).isNotEqualTo(PushJobStatus.SENT);
        awaitStatus(failingJob, PushJobStatus.SENT);
        assertThat(pushSender.callsOf(failing.token())).isEqualTo(2);
    }

    @Test
    @DisplayName("두 번 모두 일시 오류면 FAILED 로 기록하고 메시지를 DLT 로 보낸다")
    void sendsToDeadLetterAfterTwoAttempts() {
        Recipient recipient = recipientWithToken();
        pushSender.script(recipient.token(), TokenSendOutcome.TRANSIENT_FAILURE, TokenSendOutcome.TRANSIENT_FAILURE);
        Long jobId = publishedJob(recipient);

        sendKeyed(0, recipient, jobId);

        await().atMost(WAIT).until(() -> deadLetterJobIds().contains(jobId));
        assertThat(job(jobId).getStatus()).isEqualTo(PushJobStatus.FAILED);
        assertThat(pushSender.callsOf(recipient.token())).isEqualTo(2);
    }

    private Long publishedJob(Recipient recipient) {
        return persistJob(recipient, NotificationType.MATCHING_COMPLETED, PushJobStatus.PUBLISHED, LocalDateTime.now());
    }

    private Long notificationIdOf(Long jobId) {
        return jobRepository.findWithNotificationById(jobId).orElseThrow().getNotification().getId();
    }

    private void sendKeyed(int partition, Recipient recipient, Long jobId) {
        String value = jsonMapper.writeValueAsString(envelopeFactory.create(
                NotificationPushMessages.PUSH_REQUESTED, NotificationPushMessages.SCHEMA_VERSION,
                new NotificationPushRequestData(jobId)));
        kafkaTemplate.send(new ProducerRecord<>(NotificationPushMessages.PUSH_TOPIC, partition,
                String.valueOf(recipient.user().getId()), value));
    }
}
