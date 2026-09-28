package com.ktb.moyeota.domain.chat.event;

import lombok.RequiredArgsConstructor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
@RequiredArgsConstructor
public class ChatMessageBroadcaster {

    private static final String BROKER_DESTINATION_PREFIX = "/sub/chat/";

    private final SimpMessagingTemplate messagingTemplate;

    @TransactionalEventListener
    public void broadcast(ChatMessageCreatedEvent event) {
        messagingTemplate.convertAndSend(BROKER_DESTINATION_PREFIX + event.roomId(), event.message());
    }
}
