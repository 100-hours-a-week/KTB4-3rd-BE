package com.ktb.moyeota.domain.chat.dto;

import java.util.List;

public record MessageListResponse<T extends ChatMessageView>(
        List<T> items,
        String beforeCursor,
        String afterCursor
) {
}
