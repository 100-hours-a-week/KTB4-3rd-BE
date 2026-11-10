package com.ktb.moyeota.domain.settlement.message;

public final class SettlementChangedMessages {

    public static final String TOPIC = "settlement.changed";
    public static final int SCHEMA_VERSION = 1;

    public static final String TRANSFER_REQUESTED = "settlement.transfer.requested";
    public static final String TRANSFER_EXHAUSTED = "settlement.transfer.exhausted";
    public static final String COMPLETED = "settlement.completed";

    private SettlementChangedMessages() {
    }

    public static String key(Long companionId) {
        return String.valueOf(companionId);
    }
}
