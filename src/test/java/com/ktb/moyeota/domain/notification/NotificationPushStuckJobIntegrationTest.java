package com.ktb.moyeota.domain.notification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.ktb.moyeota.domain.notification.entity.NotificationPushJob;
import com.ktb.moyeota.domain.notification.entity.NotificationType;
import com.ktb.moyeota.domain.notification.entity.PushJobStatus;
import com.ktb.moyeota.domain.notification.message.NotificationPushMessages;
import com.ktb.moyeota.domain.notification.service.NotificationPushRepublisher;
import java.time.LocalDateTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.support.TransactionTemplate;

class NotificationPushStuckJobIntegrationTest extends NotificationPushKafkaIntegrationSupport {

    private static final int UNSUPPORTED_VERSION = NotificationPushMessages.SCHEMA_VERSION + 1;
    private static final int MAX_ATTEMPTS = 5;

    @Autowired
    private NotificationPushRepublisher republisher;

    @Test
    @DisplayName("읽지 못하는 버전으로 DLT 에 간 작업은 PUBLISHED 로 남았다가 스윕이 지금 버전으로 다시 보낸다")
    void sweepResendsJobWhoseMessageWentToDeadLetter() {
        Recipient recipient = recipientWithToken();
        Long jobId = persistJob(recipient, NotificationType.MATCHING_COMPLETED, PushJobStatus.PUBLISHED,
                minutesAgo(30));
        sendRequest(NotificationPushMessages.PUSH_TOPIC, NotificationPushMessages.PUSH_REQUESTED,
                UNSUPPORTED_VERSION, null, jobId);
        await().atMost(WAIT).until(() -> deadLetterJobIds().contains(jobId));
        assertThat(job(jobId).getStatus()).isEqualTo(PushJobStatus.PUBLISHED);

        int republished = republisher.republishDue();

        assertThat(republished).isEqualTo(1);
        awaitStatus(jobId, PushJobStatus.SENT);
        assertThat(pushSender.callsOf(recipient.token())).isEqualTo(1);
    }

    @Test
    @DisplayName("메시지를 잃어 오래 PUBLISHED 로 남은 작업은 스윕이 다시 발행해 보낸다")
    void sweepRecoversJobStuckInPublished() {
        Recipient recipient = recipientWithToken();
        Long jobId = persistJob(recipient, NotificationType.MATCHING_COMPLETED, PushJobStatus.PUBLISHED,
                minutesAgo(30));

        int republished = republisher.republishDue();

        assertThat(republished).isEqualTo(1);
        awaitStatus(jobId, PushJobStatus.SENT);
        assertThat(pushSender.callsOf(recipient.token())).isEqualTo(1);
    }

    @Test
    @DisplayName("PUBLISHED 가 된 지 얼마 안 된 작업은 소비자를 기다리고 스윕이 건드리지 않는다")
    void sweepLeavesRecentlyPublishedJob() {
        Recipient recipient = recipientWithToken();
        Long jobId = persistJob(recipient, NotificationType.MATCHING_COMPLETED, PushJobStatus.PUBLISHED,
                LocalDateTime.now());

        republisher.republishDue();

        assertThat(job(jobId).getStatus()).isEqualTo(PushJobStatus.PUBLISHED);
        assertThat(pushSender.callsOf(recipient.token())).isZero();
    }

    @Test
    @DisplayName("시도 횟수가 상한에 닿은 작업은 다시 보내지 않고 FAILED 로 끝낸다")
    void sweepGivesUpAtMaxAttempts() {
        Recipient recipient = recipientWithToken();
        Long jobId = persistJob(recipient, NotificationType.MATCHING_COMPLETED, PushJobStatus.SENDING,
                minutesAgo(30));
        setAttempts(jobId, MAX_ATTEMPTS);

        int republished = republisher.republishDue();

        assertThat(republished).isZero();
        NotificationPushJob job = job(jobId);
        assertThat(job.getStatus()).isEqualTo(PushJobStatus.FAILED);
        assertThat(job.getLastError()).isEqualTo("SWEEP_ATTEMPTS_EXHAUSTED");
        assertThat(pushSender.callsOf(recipient.token())).isZero();
    }

    @Test
    @DisplayName("시도 횟수가 상한보다 하나 적은 작업은 한 번 더 보낸다")
    void sweepResendsJustBelowMaxAttempts() {
        Recipient recipient = recipientWithToken();
        Long jobId = persistJob(recipient, NotificationType.MATCHING_COMPLETED, PushJobStatus.SENDING,
                minutesAgo(30));
        setAttempts(jobId, MAX_ATTEMPTS - 1);

        int republished = republisher.republishDue();

        assertThat(republished).isEqualTo(1);
        awaitStatus(jobId, PushJobStatus.SENT);
        assertThat(job(jobId).getAttempts()).isEqualTo(MAX_ATTEMPTS);
        assertThat(pushSender.callsOf(recipient.token())).isEqualTo(1);
    }

    private void setAttempts(Long jobId, int attempts) {
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            NotificationPushJob job = entityManager.find(NotificationPushJob.class, jobId);
            ReflectionTestUtils.setField(job, "attempts", attempts);
        });
    }
}
