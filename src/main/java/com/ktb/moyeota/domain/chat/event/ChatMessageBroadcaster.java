package com.ktb.moyeota.domain.chat.event;

import com.ktb.moyeota.global.websocket.relay.WsRelayPublisher;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
@RequiredArgsConstructor
public class ChatMessageBroadcaster {

    private static final String BROKER_DESTINATION_PREFIX = "/sub/chat/";

    private final WsRelayPublisher wsRelayPublisher;

    @TransactionalEventListener
    public void broadcast(ChatMessageCreatedEvent event) {
        wsRelayPublisher.toRoom(event.roomId(), BROKER_DESTINATION_PREFIX + event.roomId(), event.message());
    }
}
