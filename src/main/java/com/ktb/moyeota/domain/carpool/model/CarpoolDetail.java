package com.ktb.moyeota.domain.carpool.model;

import com.ktb.moyeota.domain.companion.entity.CompanionStatus;
import java.time.LocalDateTime;
import java.util.List;

public record CarpoolDetail(
        Long id,
        CompanionStatus status,
        Member host,
        String originName,
        String destName,
        LocalDateTime departureAt,
        String carModel,
        int currentCount,
        int capacity,
        boolean full,
        List<Member> participants) {

    public record Member(Long id, String name, String profileImageUrl) {
    }
}
