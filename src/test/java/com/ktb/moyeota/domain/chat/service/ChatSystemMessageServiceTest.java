package com.ktb.moyeota.domain.chat.service;

import static com.ktb.moyeota.fixture.CompanionFixture.taxiPot;
import static com.ktb.moyeota.fixture.UserFixture.user;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

import com.ktb.moyeota.domain.chat.entity.ChatRoom;
import com.ktb.moyeota.domain.chat.entity.Message;
import com.ktb.moyeota.domain.chat.entity.MessageType;
import com.ktb.moyeota.domain.chat.event.ChatMessageCreatedEvent;
import com.ktb.moyeota.domain.chat.repository.MessageRepository;
import com.ktb.moyeota.domain.companion.entity.CompanionStatus;
import com.ktb.moyeota.domain.user.entity.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class ChatSystemMessageServiceTest {

    private static final Long ROOM_ID = 501L;
    private static final Long MESSAGE_ID = 900L;

    @Mock
    private MessageRepository messageRepository;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    @InjectMocks
    private ChatSystemMessageService service;

    private final User joiner = user(7L, "합류자");
    private ChatRoom chatRoom;

    @BeforeEach
    void setUp() {
        chatRoom = ChatRoom.create(taxiPot(user(1L, "방장"), CompanionStatus.RECRUITING));
        ReflectionTestUtils.setField(chatRoom, "id", ROOM_ID);
        given(messageRepository.save(any(Message.class))).willAnswer(invocation -> {
            Message message = invocation.getArgument(0);
            ReflectionTestUtils.setField(message, "id", MESSAGE_ID);
            return message;
        });
    }

    @Test
    @DisplayName("입장하면 입장한 사람의 시스템 메시지를 음수 멱등키로 저장한다")
    void savesJoinMessageWithNegativeKey() {
        service.enter(chatRoom, joiner);

        ArgumentCaptor<Message> saved = ArgumentCaptor.forClass(Message.class);
        verify(messageRepository).save(saved.capture());
        assertThat(saved.getValue().getMessageType()).isEqualTo(MessageType.SYSTEM_JOIN);
        assertThat(saved.getValue().getSender()).isEqualTo(joiner);
        assertThat(saved.getValue().getClientMessageId()).isNegative();
    }

    @Test
    @DisplayName("입장 메시지가 채팅방의 마지막 메시지가 된다")
    void updatesLastMessageId() {
        service.enter(chatRoom, joiner);

        assertThat(chatRoom.getLastMessageId()).isEqualTo(MESSAGE_ID);
    }

    @Test
    @DisplayName("운행 시작 확인 카드를 보낸 사람 없이 음수 멱등키로 저장한다")
    void savesRideStartRequestWithNegativeKey() {
        service.requestRideStart(chatRoom);

        ArgumentCaptor<Message> saved = ArgumentCaptor.forClass(Message.class);
        verify(messageRepository).save(saved.capture());
        assertThat(saved.getValue().getMessageType()).isEqualTo(MessageType.SYSTEM_RIDE_START_REQUESTED);
        assertThat(saved.getValue().getSender()).isNull();
        assertThat(saved.getValue().getClientMessageId()).isNegative();
    }

    @Test
    @DisplayName("운행 시작 확인 카드를 채팅방 구독자에게 보낼 이벤트로 발행한다")
    void publishesRideStartRequest() {
        service.requestRideStart(chatRoom);

        ArgumentCaptor<ChatMessageCreatedEvent> event = ArgumentCaptor.forClass(ChatMessageCreatedEvent.class);
        verify(eventPublisher).publishEvent(event.capture());
        assertThat(event.getValue().roomId()).isEqualTo(ROOM_ID);
        assertThat(event.getValue().message().type()).isEqualTo(MessageType.SYSTEM_RIDE_START_REQUESTED);
    }

    @Test
    @DisplayName("운행 종료 확인 카드를 보낸 사람 없이 음수 멱등키로 저장한다")
    void savesRideEndRequestWithNegativeKey() {
        service.requestRideEnd(chatRoom);

        ArgumentCaptor<Message> saved = ArgumentCaptor.forClass(Message.class);
        verify(messageRepository).save(saved.capture());
        assertThat(saved.getValue().getMessageType()).isEqualTo(MessageType.SYSTEM_RIDE_END_REQUESTED);
        assertThat(saved.getValue().getSender()).isNull();
        assertThat(saved.getValue().getClientMessageId()).isNegative();
    }

    @Test
    @DisplayName("운행 종료 확인 카드를 채팅방 구독자에게 보낼 이벤트로 발행한다")
    void publishesRideEndRequest() {
        service.requestRideEnd(chatRoom);

        ArgumentCaptor<ChatMessageCreatedEvent> event = ArgumentCaptor.forClass(ChatMessageCreatedEvent.class);
        verify(eventPublisher).publishEvent(event.capture());
        assertThat(event.getValue().roomId()).isEqualTo(ROOM_ID);
        assertThat(event.getValue().message().type()).isEqualTo(MessageType.SYSTEM_RIDE_END_REQUESTED);
    }

    @Test
    @DisplayName("입장 메시지를 채팅방 구독자에게 보낼 이벤트로 발행한다")
    void publishesCreatedEvent() {
        service.enter(chatRoom, joiner);

        ArgumentCaptor<ChatMessageCreatedEvent> event = ArgumentCaptor.forClass(ChatMessageCreatedEvent.class);
        verify(eventPublisher).publishEvent(event.capture());
        assertThat(event.getValue().roomId()).isEqualTo(ROOM_ID);
        assertThat(event.getValue().message().id()).isEqualTo(MESSAGE_ID);
        assertThat(event.getValue().message().joiner().id()).isEqualTo(7L);
    }
}
