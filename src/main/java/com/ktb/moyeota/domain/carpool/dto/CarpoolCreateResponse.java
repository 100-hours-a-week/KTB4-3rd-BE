package com.ktb.moyeota.domain.carpool.dto;

import com.ktb.moyeota.domain.carpool.model.CreatedCarpool;

public record CarpoolCreateResponse(Long id, Long chatRoomId, int capacity, int currentCount, String status) {

    public static CarpoolCreateResponse from(CreatedCarpool carpool) {
        return new CarpoolCreateResponse(
                carpool.id(), carpool.chatRoomId(), carpool.capacity(), carpool.currentCount(),
                carpool.status().name());
    }
}
