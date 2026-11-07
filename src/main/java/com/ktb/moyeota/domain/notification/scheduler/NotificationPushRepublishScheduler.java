package com.ktb.moyeota.domain.notification.scheduler;

import com.ktb.moyeota.domain.notification.service.NotificationPushRepublisher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "moyeota.notification.push", name = "mode", havingValue = "kafka")
public class NotificationPushRepublishScheduler {

    private final NotificationPushRepublisher republisher;

    @Scheduled(fixedDelayString = "${moyeota.notification.push.sweep-delay:10s}")
    public void republish() {
        try {
            int republished = republisher.republishDue();
            if (republished > 0) {
                log.info("[PUSH_REPUBLISHED] jobs={}", republished);
            }
        } catch (RuntimeException e) {
            log.warn("[PUSH_REPUBLISH_FAILED]", e);
        }
    }
}
