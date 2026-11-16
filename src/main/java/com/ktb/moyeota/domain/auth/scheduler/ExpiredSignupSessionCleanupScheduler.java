package com.ktb.moyeota.domain.auth.scheduler;

import com.ktb.moyeota.domain.auth.service.ExpiredSignupSessionCleanupService;
import com.ktb.moyeota.global.config.ConditionalOnServerRole;
import com.ktb.moyeota.global.config.ServerRole;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnServerRole({ServerRole.WORKER, ServerRole.ALL})
public class ExpiredSignupSessionCleanupScheduler {

    private final ExpiredSignupSessionCleanupService expiredSignupSessionCleanupService;

    @Scheduled(fixedDelayString = "${moyeota.auth.expired-signup-session-sweep-delay}")
    public void sweep() {
        try {
            int deleted = expiredSignupSessionCleanupService.deleteExpired();
            if (deleted > 0) {
                log.info("[EXPIRED_SIGNUP_SESSION_CLEANUP] deleted={}", deleted);
            }
        } catch (RuntimeException e) {
            log.warn("[EXPIRED_SIGNUP_SESSION_CLEANUP_FAILED]", e);
        }
    }
}
