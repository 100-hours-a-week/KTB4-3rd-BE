package com.ktb.moyeota.domain.notification.scheduler;

import com.ktb.moyeota.domain.notification.service.SettlementReminderService;
import com.ktb.moyeota.global.config.ConditionalOnServerRole;
import com.ktb.moyeota.global.config.ServerRole;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@ConditionalOnServerRole({ServerRole.WORKER, ServerRole.ALL})
public class SettlementCompletionReminderScheduler {

    private final SettlementReminderService reminderService;
    private final Clock clock;
    private final int chunkSize;

    public SettlementCompletionReminderScheduler(
            SettlementReminderService reminderService,
            Clock clock,
            @Value("${moyeota.notification.settlement-reminder.chunk-size:100}") int chunkSize) {
        this.reminderService = reminderService;
        this.clock = clock;
        this.chunkSize = chunkSize;
    }

    @Scheduled(cron = "${moyeota.notification.settlement-reminder.cron:0 0 9,21 * * *}", zone = "Asia/Seoul")
    public void remind() {
        LocalDateTime round = LocalDateTime.now(clock).truncatedTo(ChronoUnit.HOURS);
        List<Long> settlementIds;
        try {
            settlementIds = reminderService.findSettlementIdsToRemind();
        } catch (RuntimeException e) {
            log.warn("[SETTLEMENT_REMINDER_LOOKUP_FAILED]", e);
            return;
        }

        for (int from = 0; from < settlementIds.size(); from += chunkSize) {
            remindChunk(settlementIds.subList(from, Math.min(from + chunkSize, settlementIds.size())), round);
        }
    }

    private void remindChunk(List<Long> chunk, LocalDateTime round) {
        try {
            reminderService.remindAll(chunk, round);
        } catch (RuntimeException e) {
            log.warn("[SETTLEMENT_REMINDER_CHUNK_FAILED] size={}, firstSettlementId={}", chunk.size(), chunk.get(0), e);
            chunk.forEach(settlementId -> remindOne(settlementId, round));
        }
    }

    private void remindOne(Long settlementId, LocalDateTime round) {
        try {
            reminderService.remind(settlementId, round);
        } catch (RuntimeException e) {
            log.warn("[SETTLEMENT_REMINDER_FAILED] settlementId={}", settlementId, e);
        }
    }
}
