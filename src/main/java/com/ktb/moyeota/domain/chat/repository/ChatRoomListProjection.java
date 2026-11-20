package com.ktb.moyeota.domain.chat.repository;

import java.time.LocalDateTime;

public interface ChatRoomListProjection extends ChatRoomSummary {

    Long getLastMessageId();

    Long getLastReadMessageId();

    LocalDateTime getLastMessageAt();
}
