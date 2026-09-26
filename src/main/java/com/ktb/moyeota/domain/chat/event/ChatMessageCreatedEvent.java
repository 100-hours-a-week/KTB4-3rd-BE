package com.ktb.moyeota.domain.chat.event;

import com.ktb.moyeota.domain.chat.dto.MessageItem;

public record ChatMessageCreatedEvent(Long roomId, MessageItem message) {
}
