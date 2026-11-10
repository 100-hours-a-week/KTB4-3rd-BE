package com.ktb.moyeota.domain.taxipot.event;

import java.util.List;

public record TaxiPotMatchedEvent(
        Long taxiPotId,
        Long chatRoomId,
        List<Long> matchedUserIds) {
}
