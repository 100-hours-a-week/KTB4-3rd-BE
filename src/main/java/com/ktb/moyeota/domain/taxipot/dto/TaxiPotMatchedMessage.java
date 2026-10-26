package com.ktb.moyeota.domain.taxipot.dto;

import com.ktb.moyeota.domain.taxipot.event.TaxiPotMatchedEvent;

public record TaxiPotMatchedMessage(
        String type,
        Long taxiPotId,
        Long chatRoomId) {

    private static final String TYPE = "MATCHED";

    public static TaxiPotMatchedMessage from(TaxiPotMatchedEvent event) {
        return new TaxiPotMatchedMessage(TYPE, event.taxiPotId(), event.chatRoomId());
    }
}
