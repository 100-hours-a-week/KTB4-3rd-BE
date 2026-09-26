package com.ktb.moyeota.domain.chat.dto;

import com.ktb.moyeota.domain.chat.repository.ChatRoomListProjection;
import com.ktb.moyeota.domain.companion.entity.CompanionKind;


public record ChatRoomItem(
        Long id,
        Long companionId,
        CompanionKind kind,
        String title,
        Host host,
        Integer currentCount,
        Integer capacity,
        boolean hasUnread
) {

    public record Host(String profileImageUrl) {
    }

    public static ChatRoomItem of(ChatRoomListProjection row, boolean hasUnread) {
        String title = row.getDepartureAt().getHour() + "시 " + row.getOriginName();

        return new ChatRoomItem(
                row.getId(),
                row.getCompanionId(),
                CompanionKind.valueOf(row.getKind()),
                title,
                new Host(row.getHostProfileImageUrl()),
                row.getCurrentCount(),
                row.getCapacity(),
                hasUnread
        );
    }
}
