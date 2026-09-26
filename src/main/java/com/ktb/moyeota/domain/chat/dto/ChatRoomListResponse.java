package com.ktb.moyeota.domain.chat.dto;

import java.util.List;

public record ChatRoomListResponse(
        List<ChatRoomItem> items,
        String nextCursor
) {
}
