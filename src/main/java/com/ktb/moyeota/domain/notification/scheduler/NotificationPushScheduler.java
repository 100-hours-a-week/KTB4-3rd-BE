package com.ktb.moyeota.domain.notification.scheduler;

import com.ktb.moyeota.domain.notification.service.NotificationPushService;
import com.ktb.moyeota.global.config.NotificationPushConfig;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.task.TaskExecutor;
import org.springframework.core.task.TaskRejectedException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;


@Slf4j
@Component
public class NotificationPushScheduler {

    private final NotificationPushService pushService;
    private final TaskExecutor pushExecutor;

    public NotificationPushScheduler(NotificationPushService pushService,
                                     @Qualifier(NotificationPushConfig.PUSH_EXECUTOR) TaskExecutor pushExecutor) {
        this.pushService = pushService;
        this.pushExecutor = pushExecutor;
    }

    @Scheduled(fixedDelayString = "${moyeota.notification.push.sweep-delay:10s}")
    public void sweep() {
        List<Long> jobIds;
        try {
            jobIds = pushService.claimDueJobs();
        } catch (RuntimeException e) {
            log.warn("[PUSH_SWEEP_CLAIM_FAILED]", e);
            return;
        }

        for (Long jobId : jobIds) {
            try {
                pushExecutor.execute(() -> process(jobId));
            } catch (TaskRejectedException e) {
                log.warn("[PUSH_SWEEP_REJECTED] jobId={}", jobId);
            }
        }
    }

    private void process(Long jobId) {
        try {
            pushService.process(jobId);
        } catch (RuntimeException e) {
            log.warn("[PUSH_SWEEP_FAILED] jobId={}", jobId, e);
        }
    }
}
