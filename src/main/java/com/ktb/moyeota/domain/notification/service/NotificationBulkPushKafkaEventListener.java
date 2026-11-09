package com.ktb.moyeota.domain.notification.service;

import com.ktb.moyeota.domain.notification.event.NotificationBulkPushRequestedEvent;
import com.ktb.moyeota.domain.notification.message.NotificationPushChannel;
import com.ktb.moyeota.global.config.NotificationPushConfig;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "moyeota.notification.push", name = "mode", havingValue = "kafka")
public class NotificationBulkPushKafkaEventListener {

    private final NotificationPushPublisher publisher;

    @Async(NotificationPushConfig.PUSH_EXECUTOR)
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onBulkPushRequested(NotificationBulkPushRequestedEvent event) {
        publisher.publish(NotificationPushChannel.BULK, event.jobIds());
    }
}
