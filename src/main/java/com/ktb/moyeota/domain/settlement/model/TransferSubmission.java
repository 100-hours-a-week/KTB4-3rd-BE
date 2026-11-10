package com.ktb.moyeota.domain.settlement.model;

import com.ktb.moyeota.domain.settlement.entity.SettlementStatus;

public record TransferSubmission(SettlementStatus status, int remainingTransferAttempts) {
}
