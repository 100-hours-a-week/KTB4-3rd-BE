package com.ktb.moyeota.domain.chat.dto;

import java.util.List;

public record MessageListResponse(
        List<MessageItem> items,
        String nextCursor
) {
}
