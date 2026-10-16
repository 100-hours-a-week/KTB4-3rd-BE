package com.ktb.moyeota.global.websocket.relay;

import lombok.RequiredArgsConstructor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class StompUserRelayHandler implements UserRelayHandler {

    private final SimpMessagingTemplate messagingTemplate;

    @Override
    public void handle(Long userId, RelayMessage message) {
        messagingTemplate.convertAndSendToUser(String.valueOf(userId), message.destination(), message.payload());
    }
}
