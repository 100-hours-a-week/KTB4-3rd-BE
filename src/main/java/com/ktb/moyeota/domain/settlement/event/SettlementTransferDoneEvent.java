package com.ktb.moyeota.domain.settlement.event;

public record SettlementTransferDoneEvent(Long settlementId, Long companionId, Long userId) {
}
