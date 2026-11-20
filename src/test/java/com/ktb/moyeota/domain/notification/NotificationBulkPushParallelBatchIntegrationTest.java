package com.ktb.moyeota.domain.notification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.BDDMockito.given;

import com.ktb.moyeota.domain.notification.consumer.NotificationBulkPushConsumer;
import com.ktb.moyeota.domain.notification.entity.NotificationType;
import com.ktb.moyeota.domain.notification.entity.PushJobStatus;
import com.ktb.moyeota.domain.notification.message.NotificationPushChannel;
import com.ktb.moyeota.domain.notification.message.NotificationPushMessages;
import com.ktb.moyeota.domain.notification.push.TokenSendOutcome;
import com.ktb.moyeota.domain.notification.service.NotificationPushPublisher;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.stream.IntStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.TestPropertySource;

@TestPropertySource(properties = "moyeota.notification.push.kafka.bulk-parallel.batch-size=10")
class NotificationBulkPushParallelBatchIntegrationTest extends NotificationBulkPushParallelIntegrationTest {

    @Autowired
    private NotificationPushPublisher batchPublisher;

    @BeforeEach
    void tokensForBatch() {
        given(pushTokenRepository.findUserIdAndTokenByUserIdIn(anyCollection())).willAnswer(invocation -> {
            Collection<Long> userIds = invocation.getArgument(0);
            return userIds.stream().map(userId -> new Object[] {userId, tokenOf(userId)}).toList();
        });
    }

    @Override
    @Test
    @DisplayName("묶음 안은 차례로 보내고 묶음끼리는 동시에 처리한다")
    void sendsMoreThanPartitionsAtOnce() {
        pushSender.delay(Duration.ofMillis(500));
        List<Long> jobIds = IntStream.range(0, 48)
                .mapToObj(i -> persistJob(recipientWithToken(), NotificationType.SETTLEMENT_COMPLETION_REMINDER,
                        PushJobStatus.PUBLISHED, LocalDateTime.now()))
                .toList();
        long startedAt = System.nanoTime();

        batchPublisher.publish(NotificationPushChannel.BULK, jobIds);

        await().atMost(WAIT).until(() -> jobIds.stream().allMatch(id -> job(id).getStatus() == PushJobStatus.SENT));
        Duration elapsed = Duration.ofNanos(System.nanoTime() - startedAt);
        pushSender.delay(Duration.ZERO);
        assertThat(elapsed).isLessThan(Duration.ofMillis(500L * jobIds.size() / 2));
    }

    @Test
    @DisplayName("묶음에서 한 건만 일시 오류여도 다른 건은 한 번만 보낸다")
    void retriesOnlyFailedJobInBatch() {
        List<Recipient> recipients = IntStream.range(0, 10).mapToObj(i -> recipientWithToken()).toList();
        Recipient failing = recipients.getFirst();
        pushSender.script(failing.token(), TokenSendOutcome.TRANSIENT_FAILURE, TokenSendOutcome.SUCCESS);
        List<Long> jobIds = recipients.stream()
                .map(recipient -> persistJob(recipient, NotificationType.SETTLEMENT_COMPLETION_REMINDER,
                        PushJobStatus.PUBLISHED, LocalDateTime.now()))
                .toList();

        batchPublisher.publish(NotificationPushChannel.BULK, jobIds);

        await().atMost(WAIT).until(() -> jobIds.stream().allMatch(id -> job(id).getStatus() == PushJobStatus.SENT));
        awaitDrained(NotificationBulkPushConsumer.GROUP_ID, NotificationPushMessages.BULK_PUSH_TOPIC);
        assertThat(pushSender.callsOf(failing.token())).isEqualTo(2);
        assertThat(recipients.subList(1, recipients.size()))
                .allSatisfy(recipient -> assertThat(pushSender.callsOf(recipient.token())).isEqualTo(1));
        assertThat(deadLetterJobIds()).doesNotContainAnyElementsOf(jobIds);
    }
}
