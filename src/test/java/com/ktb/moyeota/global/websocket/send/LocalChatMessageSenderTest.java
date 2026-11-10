package com.ktb.moyeota.global.websocket.send;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

import com.ktb.moyeota.domain.chat.dto.ChatMessageSendRequest;
import com.ktb.moyeota.domain.chat.dto.MessageItem;
import com.ktb.moyeota.domain.chat.entity.MessageType;
import com.ktb.moyeota.domain.chat.service.ChatMessageSendService;
import com.ktb.moyeota.global.websocket.relay.RecordingWsRelayPublisher;
import com.ktb.moyeota.global.websocket.relay.RecordingWsRelayPublisher.Published;
import java.time.LocalDateTime;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class LocalChatMessageSenderTest {

    private static final Long ROOM_ID = 30L;
    private static final Long USER_ID = 42L;

    @Mock
    private ChatMessageSendService chatMessageSendService;

    private final RecordingWsRelayPublisher wsRelayPublisher = new RecordingWsRelayPublisher();
    private LocalChatMessageSender sender;

    @BeforeEach
    void setUp() {
        sender = new LocalChatMessageSender(chatMessageSendService, wsRelayPublisher);
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

        sender.send(ROOM_ID, USER_ID, request);

        assertThat(wsRelayPublisher.published())
                .containsExactly(new Published("room", ROOM_ID, "/sub/chat/" + ROOM_ID, item));
    }

    @Test
    @DisplayName("저장되지 않으면(참여자 아님) 브로드캐스트하지 않는다")
    void doesNotBroadcastWhenEmpty() {
        ChatMessageSendRequest request = new ChatMessageSendRequest("1", "안녕하세요");
        given(chatMessageSendService.send(USER_ID, ROOM_ID, request)).willReturn(Optional.empty());

        sender.send(ROOM_ID, USER_ID, request);

        assertThat(wsRelayPublisher.published()).isEmpty();
    }
}
