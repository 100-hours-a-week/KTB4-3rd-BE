package com.ktb.moyeota.domain.carpool.dto;

import com.ktb.moyeota.domain.carpool.model.SentJoinRequest;
import java.time.LocalDateTime;

public record JoinRequestSendResponse(Long id, Long carpoolId, String status, LocalDateTime createdAt) {

    public static JoinRequestSendResponse from(SentJoinRequest request) {
        return new JoinRequestSendResponse(
                request.id(), request.carpoolId(), request.status().name(), request.createdAt());
    }
}
