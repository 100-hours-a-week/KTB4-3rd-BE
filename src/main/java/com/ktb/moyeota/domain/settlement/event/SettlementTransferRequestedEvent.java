package com.ktb.moyeota.domain.settlement.event;

public record SettlementTransferRequestedEvent(Long settlementId, Long companionId) {
}
