package com.ktb.moyeota.domain.settlement.model;

import com.ktb.moyeota.domain.settlement.entity.SettlementStatus;
import com.ktb.moyeota.domain.settlement.entity.TransferAnalysisStatus;
import java.time.LocalDateTime;
import java.util.Optional;

public record MyTransfer(int participantCount, SettlementStatus status, Optional<Charge> charge) {

    public record Charge(
            int amount,
            TransferAnalysisStatus analysisStatus,
            LocalDateTime transferredAt,
            Payee payee) {
    }

    public record Payee(String name, String bankName, String accountNo) {
    }
}
