package com.ktb.moyeota.domain.settlement.event;

import java.time.LocalDateTime;

public record MeterAnalysisRequestedEvent(Long settlementId, String meterImageKey, LocalDateTime startedAt) {
}
