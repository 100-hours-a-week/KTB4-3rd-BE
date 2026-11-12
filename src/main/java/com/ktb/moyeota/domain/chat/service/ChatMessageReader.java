package com.ktb.moyeota.domain.chat.service;

import com.ktb.moyeota.domain.chat.dto.ChatMessageView;
import com.ktb.moyeota.domain.chat.dto.MessageListResponse;

public interface ChatMessageReader {

    MessageListResponse<? extends ChatMessageView> findMessages(
            Long userId, Long roomId, String direction, String before, String after);
}
