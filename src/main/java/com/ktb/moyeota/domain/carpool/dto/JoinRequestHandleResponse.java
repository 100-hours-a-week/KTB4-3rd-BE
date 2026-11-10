package com.ktb.moyeota.domain.carpool.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.ktb.moyeota.domain.carpool.model.HandledJoinRequest;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record JoinRequestHandleResponse(
        Long id, String status, Long chatRoomId, Integer currentCount, Integer capacity) {

    public static JoinRequestHandleResponse from(HandledJoinRequest handled) {
        return new JoinRequestHandleResponse(handled.id(), handled.status().name(),
                handled.chatRoomId(), handled.currentCount(), handled.capacity());
    }
}
