package com.ktb.moyeota.domain.carpool.dto;

import com.ktb.moyeota.domain.carpool.model.JoinRequestDetail;
import java.time.LocalDateTime;

public record JoinRequestDetailResponse(
        Long id, Long carpoolId, String status, String content, Requester requester, LocalDateTime createdAt) {

    public static JoinRequestDetailResponse from(JoinRequestDetail detail) {
        return new JoinRequestDetailResponse(
                detail.id(),
                detail.carpoolId(),
                detail.status().name(),
                detail.content(),
                new Requester(detail.requester().id(), detail.requester().name(), detail.requester().profileImageUrl()),
                detail.createdAt());
    }

    public record Requester(Long id, String name, String profileImageUrl) {
    }
}
