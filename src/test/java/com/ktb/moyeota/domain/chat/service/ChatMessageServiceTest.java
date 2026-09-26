package com.ktb.moyeota.domain.chat.service;

import static com.ktb.moyeota.fixture.CompanionFixture.companionPost;
import static com.ktb.moyeota.fixture.UserFixture.user;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

import com.ktb.moyeota.domain.chat.dto.MessageCursor;
import com.ktb.moyeota.domain.chat.dto.MessageListResponse;
import com.ktb.moyeota.domain.chat.entity.ChatRoom;
import com.ktb.moyeota.domain.chat.entity.CompanionParticipant;
import com.ktb.moyeota.domain.chat.entity.Message;
import com.ktb.moyeota.domain.chat.exception.ChatErrorCode;
import com.ktb.moyeota.domain.chat.repository.CompanionParticipantRepository;
import com.ktb.moyeota.domain.chat.repository.MessageRepository;
import com.ktb.moyeota.domain.companion.entity.Companion;
import com.ktb.moyeota.domain.user.entity.User;
import com.ktb.moyeota.global.exception.BusinessException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class ChatMessageServiceTest {

    private static final Long ROOM_ID = 501L;
    private static final Long USER_ID = 7L;

    @Mock
    private MessageRepository messageRepository;

    @Mock
    private CompanionParticipantRepository companionParticipantRepository;

    private ChatMessageService service;

    private User sender;
    private ChatRoom chatRoom;
    private CompanionParticipant participant;

    @BeforeEach
    void setUp() {
        sender = user("우림");
        Companion companion = companionPost(sender);
        chatRoom = ChatRoom.create(companion);
        participant = CompanionParticipant.join(companion, sender);
        service = new ChatMessageService(messageRepository, companionParticipantRepository, new MessageCursorCodec());
    }

    @Nested
    @DisplayName("참여자 검증")
    class ParticipantCheck {

        @Test
        @DisplayName("참여자가 아니면 CHATROOM_NOT_FOUND(존재 은닉)")
        void throwsWhenNotParticipant() {
            given(companionParticipantRepository.findActiveByChatRoomIdAndUserId(ROOM_ID, USER_ID))
                    .willReturn(Optional.empty());

            assertThatThrownBy(() -> service.findMessages(USER_ID, ROOM_ID, null))
                    .isInstanceOf(BusinessException.class)
                    .extracting(e -> ((BusinessException) e).getErrorCode())
                    .isEqualTo(ChatErrorCode.CHATROOM_NOT_FOUND);
        }
    }

    @Nested
    @DisplayName("첫 페이지(cursor 없음) 기준점")
    class FirstPageAnchor {

        @Test
        @DisplayName("마지막으로 읽은 메시지가 있으면 그 지점(+1)부터 조회한다")
        void anchorsAtLastReadMessage() {
            Message lastRead = message(100L);
            ReflectionTestUtils.setField(participant, "message", lastRead);
            given(companionParticipantRepository.findActiveByChatRoomIdAndUserId(ROOM_ID, USER_ID))
                    .willReturn(Optional.of(participant));
            given(messageRepository.findByChatRoomIdWithSender(eq(ROOM_ID), any(), any(Pageable.class)))
                    .willReturn(List.of());

            service.findMessages(USER_ID, ROOM_ID, null);

            ArgumentCaptor<Long> cursorCaptor = ArgumentCaptor.forClass(Long.class);
            verify(messageRepository).findByChatRoomIdWithSender(eq(ROOM_ID), cursorCaptor.capture(), any());
            assertThat(cursorCaptor.getValue()).isEqualTo(101L);
        }

        @Test
        @DisplayName("한 번도 읽은 적 없으면(last_read_message_id NULL) 최신 메시지부터 조회한다")
        void fallsBackToLatestWhenNeverRead() {
            given(companionParticipantRepository.findActiveByChatRoomIdAndUserId(ROOM_ID, USER_ID))
                    .willReturn(Optional.of(participant));
            given(messageRepository.findByChatRoomIdWithSender(eq(ROOM_ID), isNull(), any(Pageable.class)))
                    .willReturn(List.of());

            service.findMessages(USER_ID, ROOM_ID, null);

            verify(messageRepository).findByChatRoomIdWithSender(eq(ROOM_ID), isNull(), any());
        }
    }

    @Nested
    @DisplayName("다음 페이지(cursor 있음)")
    class NextPage {

        @Test
        @DisplayName("cursor가 있으면 마지막으로 읽은 메시지를 무시하고 커서를 그대로 쓴다")
        void usesGivenCursorEvenWhenLastReadExists() {
            ReflectionTestUtils.setField(participant, "message", message(999L));
            given(companionParticipantRepository.findActiveByChatRoomIdAndUserId(ROOM_ID, USER_ID))
                    .willReturn(Optional.of(participant));
            given(messageRepository.findByChatRoomIdWithSender(eq(ROOM_ID), eq(50L), any(Pageable.class)))
                    .willReturn(List.of());

            String cursor = new MessageCursorCodec().encode(new MessageCursor(50L));
            service.findMessages(USER_ID, ROOM_ID, cursor);

            verify(messageRepository).findByChatRoomIdWithSender(eq(ROOM_ID), eq(50L), any());
        }
    }

    @Nested
    @DisplayName("페이지네이션")
    class Pagination {

        @Test
        @DisplayName("PAGE_SIZE + 1건이 조회되면 마지막 1건을 잘라내고 next_cursor를 채운다")
        void hasNextWhenOverPageSize() {
            given(companionParticipantRepository.findActiveByChatRoomIdAndUserId(ROOM_ID, USER_ID))
                    .willReturn(Optional.of(participant));

            List<Message> messages = new ArrayList<>();
            for (long i = 21; i >= 1; i--) {
                messages.add(message(i));
            }
            given(messageRepository.findByChatRoomIdWithSender(eq(ROOM_ID), any(), any(Pageable.class)))
                    .willReturn(messages);

            MessageListResponse response = service.findMessages(USER_ID, ROOM_ID, null);

            assertThat(response.items()).hasSize(20);
            assertThat(response.nextCursor()).isNotNull();
        }

        @Test
        @DisplayName("PAGE_SIZE 이하로 조회되면 next_cursor가 없다")
        void noNextWhenUnderPageSize() {
            given(companionParticipantRepository.findActiveByChatRoomIdAndUserId(ROOM_ID, USER_ID))
                    .willReturn(Optional.of(participant));
            given(messageRepository.findByChatRoomIdWithSender(eq(ROOM_ID), any(), any(Pageable.class)))
                    .willReturn(List.of(message(1L)));

            MessageListResponse response = service.findMessages(USER_ID, ROOM_ID, null);

            assertThat(response.items()).hasSize(1);
            assertThat(response.nextCursor()).isNull();
        }
    }

    private Message message(long id) {
        Message message = Message.createGeneralMessage(chatRoom, sender, id, "content-" + id);
        ReflectionTestUtils.setField(message, "id", id);
        return message;
    }
}
