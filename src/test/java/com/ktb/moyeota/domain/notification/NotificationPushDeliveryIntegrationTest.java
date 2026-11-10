package com.ktb.moyeota.domain.notification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.ktb.moyeota.domain.notification.consumer.NotificationPushConsumer;
import com.ktb.moyeota.domain.notification.entity.Notification;
import com.ktb.moyeota.domain.notification.entity.NotificationType;
import com.ktb.moyeota.domain.notification.entity.PushJobStatus;
import com.ktb.moyeota.domain.notification.message.NotificationPushChannel;
import com.ktb.moyeota.domain.notification.message.NotificationPushMessages;
import com.ktb.moyeota.domain.notification.service.NotificationCommandService;
import com.ktb.moyeota.domain.notification.service.NotificationPushPublisher;
import com.ktb.moyeota.domain.notification.service.NotificationPushRepublisher;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class NotificationPushDeliveryIntegrationTest extends NotificationPushKafkaIntegrationSupport {

    @Autowired
    private NotificationCommandService commandService;

    @Autowired
    private NotificationPushPublisher publisher;

    @Autowired
    private NotificationPushRepublisher republisher;

    @Test
    @DisplayName("알림을 저장하면 커밋 뒤 발행되고 worker 가 한 번 보내 SENT 로 끝난다")
    void sendsOnceAfterCommit() {
        Recipient recipient = recipientWithToken();

        Long jobId = createNotification(recipient);

        awaitStatus(jobId, PushJobStatus.SENT);
        assertThat(pushSender.callsOf(recipient.token())).isEqualTo(1);
        assertThat(job(jobId).getAttempts()).isEqualTo(1);
    }

    @Test
    @DisplayName("커밋 뒤 발행 전에 서버가 멈춰 PENDING 으로 남은 작업은 스윕이 다시 발행해 한 번 보낸다")
    void sweepRecoversJobNeverPublished() {
        Recipient recipient = recipientWithToken();
        Long jobId = persistJob(recipient, NotificationType.MATCHING_COMPLETED, PushJobStatus.PENDING, minutesAgo(10));

        int republished = republisher.republishDue();

        assertThat(republished).isEqualTo(1);
        awaitStatus(jobId, PushJobStatus.SENT);
        assertThat(pushSender.callsOf(recipient.token())).isEqualTo(1);
    }

    @Test
    @DisplayName("브로커가 멈춘 동안 저장한 알림은 PENDING 으로 남고, 브로커가 돌아오면 스윕이 보낸다")
    void sweepRecoversJobWhenBrokerWasDown() {
        Recipient recipient = recipientWithToken();
        Long jobId;
        pauseBroker();
        try {
            jobId = createNotification(recipient);
            await().during(Duration.ofSeconds(4)).atMost(Duration.ofSeconds(6))
                    .until(() -> job(jobId).getStatus() == PushJobStatus.PENDING);
        } finally {
            resumeBroker();
        }

        await().atMost(WAIT).pollInterval(Duration.ofSeconds(1)).until(() -> {
            republisher.republishDue();
            return job(jobId).getStatus() == PushJobStatus.SENT;
        });

        assertThat(pushSender.callsOf(recipient.token())).isEqualTo(1);
    }

    @Test
    @DisplayName("ack 를 기록하기 전에 멈춰 같은 작업이 두 번 발행돼도 한 번만 보낸다")
    void duplicatePublishSendsOnce() {
        Recipient recipient = recipientWithToken();
        Long jobId = persistJob(recipient, NotificationType.MATCHING_COMPLETED, PushJobStatus.PENDING,
                LocalDateTime.now());

        publisher.publish(NotificationPushChannel.PUSH, List.of(jobId, jobId));

        awaitStatus(jobId, PushJobStatus.SENT);
        awaitDrained(NotificationPushConsumer.GROUP_ID, NotificationPushMessages.PUSH_TOPIC);
        assertThat(pushSender.callsOf(recipient.token())).isEqualTo(1);
        assertThat(job(jobId).getAttempts()).isEqualTo(1);
    }

    @Test
    @DisplayName("여러 worker 가 같은 작업을 동시에 받아도 한 곳만 선점해 한 번 보낸다")
    void concurrentConsumersSendOnce() {
        Recipient recipient = recipientWithToken();
        Long jobId = persistJob(recipient, NotificationType.MATCHING_COMPLETED, PushJobStatus.PUBLISHED,
                LocalDateTime.now());
        pushSender.delay(Duration.ofMillis(500));
        try {
            List.of(0, 2, 4).forEach(partition -> sendRequest(NotificationPushMessages.PUSH_TOPIC,
                    NotificationPushMessages.PUSH_REQUESTED, NotificationPushMessages.SCHEMA_VERSION, partition, jobId));

            awaitStatus(jobId, PushJobStatus.SENT);
            awaitDrained(NotificationPushConsumer.GROUP_ID, NotificationPushMessages.PUSH_TOPIC);
        } finally {
            pushSender.delay(Duration.ZERO);
        }

        assertThat(pushSender.callsOf(recipient.token())).isEqualTo(1);
        assertThat(job(jobId).getAttempts()).isEqualTo(1);
    }

    @Test
    @DisplayName("보내던 중 서버가 멈춰 lease 가 지난 SENDING 작업은 스윕이 회수해 보낸다")
    void sweepRecoversJobStuckInSending() {
        Recipient recipient = recipientWithToken();
        Long jobId = persistJob(recipient, NotificationType.MATCHING_COMPLETED, PushJobStatus.SENDING, minutesAgo(10));

        int republished = republisher.republishDue();

        assertThat(republished).isEqualTo(1);
        awaitStatus(jobId, PushJobStatus.SENT);
        assertThat(pushSender.callsOf(recipient.token())).isEqualTo(1);
    }

    private Long createNotification(Recipient recipient) {
        Notification notification = commandService.createForChatRoom(recipient.user(),
                NotificationType.MATCHING_COMPLETED, System.nanoTime(), recipient.chatRoom(), "매칭이 완료됐어요");
        return jobRepository.findByNotificationId(notification.getId()).orElseThrow().getId();
    }

    private static void pauseBroker() {
        KAFKA.getDockerClient().pauseContainerCmd(KAFKA.getContainerId()).exec();
    }

    private static void resumeBroker() {
        KAFKA.getDockerClient().unpauseContainerCmd(KAFKA.getContainerId()).exec();
    }
}
