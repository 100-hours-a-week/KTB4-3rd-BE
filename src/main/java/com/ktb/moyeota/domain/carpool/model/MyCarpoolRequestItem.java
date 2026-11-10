package com.ktb.moyeota.domain.carpool.model;

import com.ktb.moyeota.domain.carpool.model.CarpoolDetail.Member;
import java.time.LocalDateTime;

public record MyCarpoolRequestItem(
        Long id,
        Long carpoolId,
        MyRequestStatus status,
        String content,
        Member counterpart,
        String originName,
        String destName,
        LocalDateTime departureAt,
        Long chatRoomId,
        LocalDateTime createdAt) {
}
