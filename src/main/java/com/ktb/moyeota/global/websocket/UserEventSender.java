package com.ktb.moyeota.global.websocket;

import com.ktb.moyeota.global.websocket.relay.WsRelayPublisher;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class UserEventSender {

    private final WsRelayPublisher wsRelayPublisher;

    public void send(Long userId, UserChannel channel, Object event) {
        wsRelayPublisher.toUser(userId, channel.destination(), event);
    }
}
