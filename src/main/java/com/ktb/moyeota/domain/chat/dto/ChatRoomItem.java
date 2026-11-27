package com.ktb.moyeota.domain.chat.dto;

import com.ktb.moyeota.domain.chat.repository.ChatRoomSummary;
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

    public static ChatRoomItem of(ChatRoomSummary row, String hostProfileImageUrl, boolean hasUnread) {
        String title = row.getDepartureAt().getHour() + "시 " + row.getOriginName();

        return new ChatRoomItem(
                row.getId(),
                row.getCompanionId(),
                CompanionKind.valueOf(row.getKind()),
                title,
                new Host(hostProfileImageUrl),
                row.getCurrentCount(),
                row.getCapacity(),
                hasUnread
        );
    }
}
