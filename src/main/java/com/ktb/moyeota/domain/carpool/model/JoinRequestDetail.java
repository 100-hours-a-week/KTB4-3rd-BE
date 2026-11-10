package com.ktb.moyeota.domain.carpool.model;

import com.ktb.moyeota.domain.carpool.model.CarpoolDetail.Member;
import java.time.LocalDateTime;

public record JoinRequestDetail(
        Long id, Long carpoolId, MyRequestStatus status, String content, Member requester, LocalDateTime createdAt) {
}
