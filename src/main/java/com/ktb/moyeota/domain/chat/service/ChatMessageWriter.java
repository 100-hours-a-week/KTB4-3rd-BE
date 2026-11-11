package com.ktb.moyeota.domain.chat.service;

import com.ktb.moyeota.domain.chat.dto.ChatMessageSendRequest;
import com.ktb.moyeota.domain.chat.dto.ChatMessageView;
import java.util.Optional;

public interface ChatMessageWriter {

    Optional<? extends ChatMessageView> send(Long userId, Long roomId, ChatMessageSendRequest request);
}
