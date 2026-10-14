package com.ktb.moyeota.global.websocket;

import lombok.RequiredArgsConstructor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class UserEventSender {

    private final SimpMessagingTemplate messagingTemplate;

    public void send(Long userId, UserChannel channel, Object event) {
        messagingTemplate.convertAndSendToUser(String.valueOf(userId), channel.destination(), event);
    }
}
