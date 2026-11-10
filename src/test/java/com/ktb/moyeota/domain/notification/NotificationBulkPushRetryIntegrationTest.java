package com.ktb.moyeota.domain.notification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.ktb.moyeota.domain.notification.consumer.NotificationBulkPushConsumer;
import com.ktb.moyeota.domain.notification.entity.NotificationPushJob;
import com.ktb.moyeota.domain.notification.entity.NotificationType;
import com.ktb.moyeota.domain.notification.entity.PushJobStatus;
import com.ktb.moyeota.domain.notification.message.NotificationPushChannel;
import com.ktb.moyeota.domain.notification.message.NotificationPushMessages;
import com.ktb.moyeota.domain.notification.push.TokenSendOutcome;
import com.ktb.moyeota.domain.notification.service.NotificationPushPublisher;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class NotificationBulkPushRetryIntegrationTest extends NotificationPushKafkaIntegrationSupport {

    private static final int MAX_ATTEMPTS = 5;

    @Autowired
    private NotificationPushPublisher publisher;

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
        assertThat(job.getSentAt()).isNotNull();
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
        assertThat(job.getLastError()).isEqualTo(TokenSendOutcome.TRANSIENT_FAILURE.name());
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

    private Long publishedReminder(Recipient recipient) {
        return persistJob(recipient, NotificationType.SETTLEMENT_COMPLETION_REMINDER, PushJobStatus.PUBLISHED,
                LocalDateTime.now());
    }
}
