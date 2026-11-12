package com.ktb.moyeota.domain.settlement.event;

public record SettlementConfirmRequestedEvent(Long settlementId, Long companionId) {
}
