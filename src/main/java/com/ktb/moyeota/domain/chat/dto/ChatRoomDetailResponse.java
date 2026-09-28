package com.ktb.moyeota.domain.chat.dto;

import com.ktb.moyeota.domain.chat.entity.ChatRoom;
import com.ktb.moyeota.domain.companion.entity.Companion;
import com.ktb.moyeota.domain.companion.entity.CompanionKind;
import com.ktb.moyeota.domain.companion.entity.CompanionStatus;
import java.time.LocalDateTime;

public record ChatRoomDetailResponse(
        Long id,
        Long companionId,
        CompanionKind kind,
        String title,
        Long hostId,
        String originName,
        String destName,
        LocalDateTime departureAt,
        Integer currentCount,
        Integer capacity,
        CompanionStatus companionStatus,
        LocalDateTime closedAt,
        Long lastReadMessageId
) {

    public static ChatRoomDetailResponse of(ChatRoom chatRoom, Long lastReadMessageId) {
        Companion companion = chatRoom.getCompanion();
        String title = companion.getDepartureAt().getHour() + "시 " + companion.getOriginName();

        return new ChatRoomDetailResponse(
                chatRoom.getId(),
                companion.getId(),
                companion.getKind(),
                title,
                companion.getHost().getId(),
                companion.getOriginName(),
                companion.getDestName(),
                companion.getDepartureAt(),
                companion.getCurrentCount(),
                companion.getCapacity(),
                companion.getStatus(),
                chatRoom.getClosedAt(),
                lastReadMessageId
        );
    }
}
