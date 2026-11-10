package com.ktb.moyeota.domain.settlement.message;

import com.ktb.moyeota.global.event.InvalidEventException;

public record SettlementChangedData(Long settlementId, Long companionId) {

    public SettlementChangedData {
        if (settlementId == null || companionId == null) {
            throw new InvalidEventException("정산 바뀜 메시지에 settlement_id · companion_id 가 있어야 합니다");
        }
    }
}
