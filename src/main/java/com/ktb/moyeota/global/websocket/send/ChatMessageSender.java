package com.ktb.moyeota.global.websocket.send;

import com.ktb.moyeota.domain.chat.dto.ChatMessageSendRequest;

public interface ChatMessageSender {

    void send(Long roomId, Long userId, ChatMessageSendRequest request);
}
