package com.ktb.moyeota.domain.chat.event;

import static org.mockito.Mockito.verify;

import com.ktb.moyeota.domain.chat.dto.MessageItem;
import com.ktb.moyeota.domain.chat.entity.MessageType;
import java.time.LocalDateTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.simp.SimpMessagingTemplate;

@ExtendWith(MockitoExtension.class)
class ChatMessageBroadcasterTest {

    @Mock
    private SimpMessagingTemplate messagingTemplate;

    @InjectMocks
    private ChatMessageBroadcaster broadcaster;

    @Test
    @DisplayName("만들어진 메시지를 채팅방 구독 경로로 보낸다")
    void sendsToRoomDestination() {
        MessageItem message = new MessageItem(900L, MessageType.SYSTEM_JOIN, null,
                new MessageItem.SystemActor(7L, "홍길동"), null, null, LocalDateTime.of(2026, 9, 5, 8, 0));

        broadcaster.broadcast(new ChatMessageCreatedEvent(501L, message));

        verify(messagingTemplate).convertAndSend("/sub/chat/501", message);
    }
}
