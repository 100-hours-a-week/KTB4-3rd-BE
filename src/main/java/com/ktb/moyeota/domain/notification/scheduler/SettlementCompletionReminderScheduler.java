package com.ktb.moyeota.domain.notification.scheduler;

import com.ktb.moyeota.domain.notification.service.SettlementReminderService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class SettlementCompletionReminderScheduler {

    private final SettlementReminderService reminderService;

    @Scheduled(cron = "${moyeota.notification.settlement-reminder.cron:0 0 9,21 * * *}", zone = "Asia/Seoul")
    public void remind() {
        List<Long> settlementIds;
        try {
            settlementIds = reminderService.findSettlementIdsToRemind();
        } catch (RuntimeException e) {
            log.warn("[SETTLEMENT_REMINDER_LOOKUP_FAILED]", e);
            return;
        }

        for (Long settlementId : settlementIds) {
            try {
                reminderService.remind(settlementId);
            } catch (RuntimeException e) {
                log.warn("[SETTLEMENT_REMINDER_FAILED] settlementId={}", settlementId, e);
            }
        }
    }
}
