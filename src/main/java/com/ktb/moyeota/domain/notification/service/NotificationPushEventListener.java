package com.ktb.moyeota.domain.notification.service;

import com.ktb.moyeota.domain.notification.event.NotificationPushRequestedEvent;
import com.ktb.moyeota.global.config.NotificationPushConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;


@Slf4j
@Component
@RequiredArgsConstructor
public class NotificationPushEventListener {

    private final NotificationPushService pushService;

    @Async(NotificationPushConfig.PUSH_EXECUTOR)
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onPushRequested(NotificationPushRequestedEvent event) {
        try {
            pushService.sendImmediately(event.jobId());
        } catch (RuntimeException e) {
            log.warn("[PUSH_IMMEDIATE_FAILED] jobId={}", event.jobId(), e);
        }
    }
}
