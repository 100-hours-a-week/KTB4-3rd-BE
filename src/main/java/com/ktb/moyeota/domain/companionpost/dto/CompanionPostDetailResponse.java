package com.ktb.moyeota.domain.companionpost.dto;

import com.ktb.moyeota.domain.companion.entity.Companion;
import com.ktb.moyeota.domain.companion.entity.TransportType;
import java.time.LocalDateTime;
import java.util.List;

/**
 * GET /companion-posts/{companion_id} 응답. title은 "출발지 → 도착지" 형태로 DTO에서 조립한다
 * (스펙 문서 지시대로 — 프론트 조립도 가능하지만 문서 기준을 따름).
 */
public record CompanionPostDetailResponse(
        Long id,
        String title,
        String originName,
        String destName,
        LocalDateTime departureAt,
        TransportType transportType,
        Integer capacity,
        Integer currentCount,
        List<Long> participantIds,
        boolean isExpired,
        boolean joined,
        String content
) {

    // [확인 필요] participants는 지금 사용자 id만 담았다. 닉네임/프로필 이미지 같은 상세 정보가
    // 필요하면 User 도메인 데이터가 있어야 해서 지금 범위(Community 문서 점검) 밖이라 채우지 않았다.
    public static CompanionPostDetailResponse of(
            Companion companion, List<Long> participantIds, boolean isExpired, boolean joined) {
        String title = companion.getOriginName() + " → " + companion.getDestName();
        return new CompanionPostDetailResponse(
                companion.getId(),
                title,
                companion.getOriginName(),
                companion.getDestName(),
                companion.getDepartureAt(),
                companion.getTransportType(),
                companion.getCapacity(),
                companion.getCurrentCount(),
                participantIds,
                isExpired,
                joined,
                companion.getContent()
        );
    }
}
