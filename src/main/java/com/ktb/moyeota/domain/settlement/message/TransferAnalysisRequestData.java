package com.ktb.moyeota.domain.settlement.message;

public record TransferAnalysisRequestData(Long settlementId, Long userId, Integer attempt, String imageUrl) {
}
