package com.ktb.moyeota.domain.chat.controller;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.ktb.moyeota.domain.chat.dto.ChatMessageSendRequest;
import com.ktb.moyeota.domain.chat.dto.MessageItem;
import com.ktb.moyeota.domain.chat.entity.MessageType;
import com.ktb.moyeota.domain.chat.service.ChatMessageSendService;
import com.ktb.moyeota.global.websocket.relay.WsRelayPublisher;
import java.security.Principal;
import java.time.LocalDateTime;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ChatStompControllerTest {

    private static final Long ROOM_ID = 30L;
    private static final Long USER_ID = 42L;

    @Mock
    private ChatMessageSendService chatMessageSendService;

    @Mock
    private WsRelayPublisher wsRelayPublisher;

    @Mock
    private Principal principal;

    private ChatStompController controller;

    @BeforeEach
    void setUp() {
        controller = new ChatStompController(chatMessageSendService, wsRelayPublisher);
        given(principal.getName()).willReturn(String.valueOf(USER_ID));
    }

    @Test
    @DisplayName("메시지가 저장되면 방 구독 destination으로 브로드캐스트한다")
    void broadcastsWhenSaved() {
        ChatMessageSendRequest request = new ChatMessageSendRequest("1", "안녕하세요");
        MessageItem item = new MessageItem(
                900L, MessageType.TEXT,
                new MessageItem.Sender(USER_ID, null, "우림", null),
                null, null, "안녕하세요", LocalDateTime.of(2026, 9, 5, 9, 0));
        given(chatMessageSendService.send(USER_ID, ROOM_ID, request)).willReturn(Optional.of(item));

        controller.send(ROOM_ID, request, principal);

        verify(wsRelayPublisher).toRoom(eq(ROOM_ID), eq("/sub/chat/" + ROOM_ID), eq(item));
    }

    @Test
    @DisplayName("저장되지 않으면(멱등 재처리 등) 브로드캐스트하지 않는다")
    void doesNotBroadcastWhenEmpty() {
        ChatMessageSendRequest request = new ChatMessageSendRequest("1", "안녕하세요");
        given(chatMessageSendService.send(USER_ID, ROOM_ID, request)).willReturn(Optional.empty());

        controller.send(ROOM_ID, request, principal);

        verifyNoInteractions(wsRelayPublisher);
    }

    @Test
    @DisplayName("principal의 이름을 userId로 변환해서 서비스에 전달한다")
    void parsesUserIdFromPrincipal() {
        ChatMessageSendRequest request = new ChatMessageSendRequest("1", "안녕하세요");
        given(chatMessageSendService.send(USER_ID, ROOM_ID, request)).willReturn(Optional.empty());

        controller.send(ROOM_ID, request, principal);

        verify(chatMessageSendService).send(USER_ID, ROOM_ID, request);
    }
}
