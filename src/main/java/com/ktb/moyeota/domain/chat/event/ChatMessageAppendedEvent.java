package com.ktb.moyeota.domain.chat.event;

import com.ktb.moyeota.domain.chat.dto.ChatMessageItem;

public record ChatMessageAppendedEvent(Long roomId, ChatMessageItem message) {
}
