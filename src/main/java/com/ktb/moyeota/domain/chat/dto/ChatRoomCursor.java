package com.ktb.moyeota.domain.chat.dto;

import java.time.LocalDateTime;

public record ChatRoomCursor(
        LocalDateTime lastMessageAt,
        Long roomId
) {
}
