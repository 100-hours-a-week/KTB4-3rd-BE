package com.ktb.moyeota.global.outbox.publisher;

import com.ktb.moyeota.global.config.ConditionalOnServerRole;
import com.ktb.moyeota.global.config.ServerRole;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnServerRole({ServerRole.REST, ServerRole.ALL})
public class OutboxPublishScheduler {

    private final OutboxPublisher outboxPublisher;

    @Scheduled(fixedDelayString = "${moyeota.outbox.publish-delay:1s}")
    @SchedulerLock(name = "outbox-publisher", lockAtMostFor = "${moyeota.outbox.lock-at-most-for:2m}")
    public void publish() {
        try {
            outboxPublisher.publishPending();
        } catch (RuntimeException e) {
            log.warn("[OUTBOX_PUBLISH_SWEEP_FAILED]", e);
        }
    }
}
