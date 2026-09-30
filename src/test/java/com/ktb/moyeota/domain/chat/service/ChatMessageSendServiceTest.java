package com.ktb.moyeota.domain.chat.service;

import static com.ktb.moyeota.fixture.CompanionFixture.companionPost;
import static com.ktb.moyeota.fixture.UserFixture.user;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.ktb.moyeota.domain.chat.dto.ChatMessageSendRequest;
import com.ktb.moyeota.domain.chat.dto.MessageItem;
import com.ktb.moyeota.domain.chat.entity.ChatRoom;
import com.ktb.moyeota.domain.chat.entity.CompanionParticipant;
import com.ktb.moyeota.domain.chat.entity.Message;
import com.ktb.moyeota.domain.chat.repository.ChatRoomRepository;
import com.ktb.moyeota.domain.chat.repository.CompanionParticipantRepository;
import com.ktb.moyeota.domain.chat.repository.MessageRepository;
import com.ktb.moyeota.domain.companion.entity.Companion;
import com.ktb.moyeota.domain.image.service.ImageUrlResolver;
import com.ktb.moyeota.domain.user.entity.User;
import com.ktb.moyeota.domain.user.repository.UserRepository;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class ChatMessageSendServiceTest {

    private static final Long ROOM_ID = 501L;
    private static final Long USER_ID = 7L;

    @Mock
    private ChatRoomRepository chatRoomRepository;

    @Mock
    private CompanionParticipantRepository companionParticipantRepository;

    @Mock
    private MessageRepository messageRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private ImageUrlResolver imageUrlResolver;

    @InjectMocks
    private ChatMessageSendService service;

    private User sender;
    private ChatRoom chatRoom;
    private CompanionParticipant participant;

    @BeforeEach
    void setUp() {
        sender = user("우림");
        ReflectionTestUtils.setField(sender, "id", USER_ID);

        Companion companion = companionPost(sender);
        chatRoom = ChatRoom.create(companion);
        ReflectionTestUtils.setField(chatRoom, "id", ROOM_ID);

        participant = CompanionParticipant.join(companion, sender);
        ReflectionTestUtils.setField(participant, "id", 10L);
    }

    @Nested
    @DisplayName("정상 전송")
    class Send {

        @Test
        @DisplayName("참여자가 보낸 메시지는 저장되고 채팅방의 last_message_id가 갱신된다")
        void savesAndUpdatesLastMessageId() {
            given(companionParticipantRepository.findActiveByChatRoomIdAndUserId(ROOM_ID, USER_ID))
                    .willReturn(Optional.of(participant));
            given(chatRoomRepository.findById(ROOM_ID)).willReturn(Optional.of(chatRoom));
            given(userRepository.findById(USER_ID)).willReturn(Optional.of(sender));

            Message saved = Message.createGeneralMessage(chatRoom, sender, "1", "안녕하세요");
            ReflectionTestUtils.setField(saved, "id", 900L);
            given(messageRepository.save(any(Message.class))).willReturn(saved);

            Optional<MessageItem> result = service.send(USER_ID, ROOM_ID,
                    new ChatMessageSendRequest("1", "안녕하세요"));

            assertThat(result).isPresent();
            assertThat(result.get().content()).isEqualTo("안녕하세요");
            assertThat(chatRoom.getLastMessageId()).isEqualTo(900L);
        }

        @Test
        @DisplayName("보낸 메시지까지 발신자의 last_read가 갱신된다")
        void updatesSenderLastRead() {
            given(companionParticipantRepository.findActiveByChatRoomIdAndUserId(ROOM_ID, USER_ID))
                    .willReturn(Optional.of(participant));
            given(chatRoomRepository.findById(ROOM_ID)).willReturn(Optional.of(chatRoom));
            given(userRepository.findById(USER_ID)).willReturn(Optional.of(sender));

            Message saved = Message.createGeneralMessage(chatRoom, sender, "1", "안녕하세요");
            ReflectionTestUtils.setField(saved, "id", 900L);
            given(messageRepository.save(any(Message.class))).willReturn(saved);

            service.send(USER_ID, ROOM_ID, new ChatMessageSendRequest("1", "안녕하세요"));

            verify(companionParticipantRepository).updateLastReadMessageIfNewer(10L, saved, 900L);
        }
    }

    @Nested
    @DisplayName("발신자 프로필 이미지")
    class SenderProfileImage {

        @Test
        @DisplayName("보낸 메시지의 발신자 이미지는 URL로 바꿔 내린다")
        void resolvesKeyToUrl() {
            sender.changeProfileImage("profile/a.png");
            given(companionParticipantRepository.findActiveByChatRoomIdAndUserId(ROOM_ID, USER_ID))
                    .willReturn(Optional.of(participant));
            given(chatRoomRepository.findById(ROOM_ID)).willReturn(Optional.of(chatRoom));
            given(userRepository.findById(USER_ID)).willReturn(Optional.of(sender));
            given(messageRepository.save(any(Message.class)))
                    .willReturn(Message.createGeneralMessage(chatRoom, sender, "1", "안녕하세요"));
            given(imageUrlResolver.toUrl("profile/a.png")).willReturn("https://cdn.moyeota.test/profile/a.png");

            Optional<MessageItem> result = service.send(USER_ID, ROOM_ID, new ChatMessageSendRequest("1", "안녕하세요"));

            assertThat(result.get().sender().profileImageUrl()).isEqualTo("https://cdn.moyeota.test/profile/a.png");
        }
    }

    @Nested
    @DisplayName("비참여자 SEND")
    class NonParticipant {

        @Test
        @DisplayName("참여자가 아니면 저장/브로드캐스트 없이 조용히 무시된다")
        void ignoresSilently() {
            given(companionParticipantRepository.findActiveByChatRoomIdAndUserId(ROOM_ID, USER_ID))
                    .willReturn(Optional.empty());

            Optional<MessageItem> result = service.send(USER_ID, ROOM_ID,
                    new ChatMessageSendRequest("1", "안녕하세요"));

            assertThat(result).isEmpty();
            verify(messageRepository, never()).save(any());
        }
    }

    @Nested
    @DisplayName("멱등성 처리")
    class Idempotency {

        @Test
        @DisplayName("같은 client_message_id로 재전송하면 기존 메시지를 그대로 재사용한다")
        void reusesExistingMessageOnDuplicateClientMessageId() {
            given(companionParticipantRepository.findActiveByChatRoomIdAndUserId(ROOM_ID, USER_ID))
                    .willReturn(Optional.of(participant));
            given(chatRoomRepository.findById(ROOM_ID)).willReturn(Optional.of(chatRoom));
            given(userRepository.findById(USER_ID)).willReturn(Optional.of(sender));
            given(messageRepository.save(any(Message.class)))
                    .willThrow(new DataIntegrityViolationException("uk_messages_room_client"));

            Message existing = Message.createGeneralMessage(chatRoom, sender, "1", "먼저 보낸 메시지");
            ReflectionTestUtils.setField(existing, "id", 800L);
            given(messageRepository.findByChatRoomIdAndClientMessageId(ROOM_ID, "1"))
                    .willReturn(Optional.of(existing));

            Optional<MessageItem> result = service.send(USER_ID, ROOM_ID,
                    new ChatMessageSendRequest("1", "재전송된 내용"));

            assertThat(result).isPresent();
            assertThat(result.get().content()).isEqualTo("먼저 보낸 메시지");
            assertThat(chatRoom.getLastMessageId()).isNull();
        }
    }
}
