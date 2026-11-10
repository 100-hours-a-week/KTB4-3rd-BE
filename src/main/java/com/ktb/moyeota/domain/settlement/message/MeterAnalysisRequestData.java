package com.ktb.moyeota.domain.settlement.message;

import java.time.OffsetDateTime;

public record MeterAnalysisRequestData(Long settlementId, OffsetDateTime analysisStartedAt, String imageUrl) {
}
