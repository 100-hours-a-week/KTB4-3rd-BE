package com.ktb.moyeota.domain.settlement.event;

import java.time.LocalDateTime;

public record TransferAnalysisRequestedEvent(
        Long settlementId, Long userId, String transferImageKey, int attempt, LocalDateTime requestedAt) {
}
