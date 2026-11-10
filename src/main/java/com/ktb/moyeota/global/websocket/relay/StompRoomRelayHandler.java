package com.ktb.moyeota.global.websocket.relay;

import lombok.RequiredArgsConstructor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class StompRoomRelayHandler implements RoomRelayHandler {

    private final SimpMessagingTemplate messagingTemplate;

    @Override
    public void handle(Long roomId, RelayMessage message) {
        messagingTemplate.convertAndSend(message.destination(), message.payload());
    }
}
