package com.ktb.moyeota.global.websocket.send;

import com.ktb.moyeota.domain.chat.dto.ChatMessageSendRequest;
import com.ktb.moyeota.domain.chat.service.ChatMessageSendService;
import com.ktb.moyeota.global.config.ConditionalOnServerRole;
import com.ktb.moyeota.global.config.ServerRole;
import com.ktb.moyeota.global.websocket.relay.WsRelayPublisher;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnServerRole({ServerRole.REST, ServerRole.ALL})
@RequiredArgsConstructor
public class LocalChatMessageSender implements ChatMessageSender {

    private static final String ROOM_DESTINATION_PREFIX = "/sub/chat/";

    private final ChatMessageSendService chatMessageSendService;
    private final WsRelayPublisher wsRelayPublisher;

    @Override
    public void send(Long roomId, Long userId, ChatMessageSendRequest request) {
        chatMessageSendService.send(userId, roomId, request)
                .ifPresent(item -> wsRelayPublisher.toRoom(roomId, ROOM_DESTINATION_PREFIX + roomId, item));
    }
}
