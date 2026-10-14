package com.ktb.moyeota.domain.settlement.model;

import com.ktb.moyeota.domain.settlement.entity.MeterAnalysisStatus;

public record MeterImage(MeterAnalysisStatus meterAnalysisStatus, String imageUrl) {
}
