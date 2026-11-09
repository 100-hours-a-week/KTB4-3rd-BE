package com.ktb.moyeota.domain.settlement.repository;

import java.time.LocalDateTime;

public record MeterAnalysisAttemptProjection(Long settlementId, LocalDateTime startedAt) {
}
