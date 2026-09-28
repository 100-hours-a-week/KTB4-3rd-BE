package com.ktb.moyeota.domain.companionpost.dto;

import com.ktb.moyeota.domain.companion.entity.Companion;
import com.ktb.moyeota.domain.companion.entity.TransportType;
import java.time.LocalDateTime;
import java.util.List;

/**
 * GET /companion-posts/{companion_id} 응답. title은 "출발지 → 도착지" 형태로 DTO에서 조립한다
 * (스펙 문서 지시대로 — 프론트 조립도 가능하지만 문서 기준을 따름).
 *
 * participants, chatRoomId는 Chat(참여자) 도메인 범위라 지금은 항상 null로 내려준다.
 */
public record CompanionPostDetailResponse(
        Long id,
        String title,
        String content,
        TransportType transportType,
        String originName,
        String destName,
        LocalDateTime departureAt,
        boolean isExpired,
        Integer currentCount,
        Integer capacity,
        boolean isFull,
        Author author,
        List<Object> participants,
        Long chatRoomId,
        boolean joined
) {

    public record Author(String nickname) {
    }

    public static CompanionPostDetailResponse of(
            Companion companion, boolean isExpired, boolean isFull, boolean joined, String authorNickname) {
        String title = companion.getOriginName() + " → " + companion.getDestName();

        return new CompanionPostDetailResponse(
                companion.getId(),
                title,
                companion.getContent(),
                companion.getTransportType(),
                companion.getOriginName(),
                companion.getDestName(),
                companion.getDepartureAt(),
                isExpired,
                companion.getCurrentCount(),
                companion.getCapacity(),
                isFull,
                new Author(authorNickname),
                null,
                null,
                joined
        );
    }
}
