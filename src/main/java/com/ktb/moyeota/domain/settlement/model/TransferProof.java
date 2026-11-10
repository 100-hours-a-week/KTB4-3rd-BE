package com.ktb.moyeota.domain.settlement.model;

import com.ktb.moyeota.domain.settlement.entity.TransferAnalysisStatus;
import java.time.LocalDateTime;

public record TransferProof(TransferAnalysisStatus analysisStatus, String imageUrl, LocalDateTime transferredAt) {
}
