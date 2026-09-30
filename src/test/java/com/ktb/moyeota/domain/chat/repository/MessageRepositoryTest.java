package com.ktb.moyeota.domain.chat.repository;

import static com.ktb.moyeota.fixture.CompanionFixture.companionPost;
import static com.ktb.moyeota.fixture.UserFixture.user;
import static org.assertj.core.api.Assertions.assertThat;

import com.ktb.moyeota.domain.chat.entity.ChatRoom;
import com.ktb.moyeota.domain.chat.entity.Message;
import com.ktb.moyeota.domain.companion.entity.Companion;
import com.ktb.moyeota.domain.user.entity.User;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

@DataJpaTest
class MessageRepositoryTest {

    @Autowired
    private MessageRepository messageRepository;

    @Autowired
    private TestEntityManager entityManager;

    private User sender;
    private ChatRoom chatRoom;

    @BeforeEach
    void setUp() {
        sender = persist(user("우림"));
        Companion companion = persist(companionPost(sender));
        chatRoom = persist(ChatRoom.create(companion));
    }

    @Nested
    @DisplayName("findByChatRoomIdWithSender")
    class FindByChatRoomIdWithSender {

        @Test
        @DisplayName("채팅방의 메시지를 최신순(id 내림차순)으로 조회한다")
        void ordersByIdDesc() {
            Message m1 = persist(message("1", "첫번째"));
            Message m2 = persist(message("2", "두번째"));
            Message m3 = persist(message("3", "세번째"));

            List<Message> result = messageRepository.findByChatRoomIdWithSender(
                    chatRoom.getId(), null, defaultPage());

            assertThat(result).extracting(Message::getId)
                    .containsExactly(m3.getId(), m2.getId(), m1.getId());
        }

        @Test
        @DisplayName("cursor가 있으면 그보다 작은 id의 메시지만 조회한다")
        void filtersByCursor() {
            Message m1 = persist(message("1", "첫번째"));
            Message m2 = persist(message("2", "두번째"));
            persist(message("3", "세번째"));

            List<Message> result = messageRepository.findByChatRoomIdWithSender(
                    chatRoom.getId(), m2.getId(), defaultPage());

            assertThat(result).extracting(Message::getId).containsExactly(m1.getId());
        }

        @Test
        @DisplayName("cursor가 없으면 전체를 조회한다")
        void nullCursorReturnsAll() {
            persist(message("1", "첫번째"));
            persist(message("2", "두번째"));

            List<Message> result = messageRepository.findByChatRoomIdWithSender(
                    chatRoom.getId(), null, defaultPage());

            assertThat(result).hasSize(2);
        }

        @Test
        @DisplayName("다른 채팅방의 메시지는 섞이지 않는다")
        void isolatesByRoom() {
            persist(message("1", "이 방 메시지"));
            Companion otherCompanion = persist(companionPost(persist(user("다른방장"))));
            ChatRoom otherRoom = persist(ChatRoom.create(otherCompanion));
            persist(Message.createGeneralMessage(otherRoom, sender, "1", "다른 방 메시지"));

            List<Message> result = messageRepository.findByChatRoomIdWithSender(
                    chatRoom.getId(), null, defaultPage());

            assertThat(result).hasSize(1);
        }

        @Test
        @DisplayName("Pageable로 개수를 제한한다")
        void limitsByPageable() {
            for (long i = 1; i <= 5; i++) {
                persist(message(String.valueOf(i), "메시지" + i));
            }

            List<Message> result = messageRepository.findByChatRoomIdWithSender(
                    chatRoom.getId(), null, PageRequest.of(0, 2));

            assertThat(result).hasSize(2);
        }

        @Test
        @DisplayName("발신자를 함께 가져온다")
        void fetchesSender() {
            persist(message("1", "안녕"));

            List<Message> result = messageRepository.findByChatRoomIdWithSender(
                    chatRoom.getId(), null, defaultPage());

            assertThat(result.get(0).getSender().getId()).isEqualTo(sender.getId());
        }

        private Pageable defaultPage() {
            return PageRequest.of(0, 20);
        }
    }

    @Nested
    @DisplayName("findNewerByChatRoomIdWithSender")
    class FindNewerByChatRoomIdWithSender {

        @Test
        @DisplayName("cursor보다 큰 id를 커서에 가까운 순(id 오름차순)으로 조회한다")
        void ordersByIdAscAfterCursor() {
            Message m1 = persist(message("1", "첫번째"));
            Message m2 = persist(message("2", "두번째"));
            Message m3 = persist(message("3", "세번째"));

            List<Message> result = messageRepository.findNewerByChatRoomIdWithSender(
                    chatRoom.getId(), m1.getId(), PageRequest.of(0, 20));

            assertThat(result).extracting(Message::getId).containsExactly(m2.getId(), m3.getId());
        }

        @Test
        @DisplayName("Pageable로 자르면 커서에 가까운 메시지부터 남는다")
        void limitsFromNearestToCursor() {
            Message m1 = persist(message("1", "첫번째"));
            Message m2 = persist(message("2", "두번째"));
            persist(message("3", "세번째"));

            List<Message> result = messageRepository.findNewerByChatRoomIdWithSender(
                    chatRoom.getId(), m1.getId(), PageRequest.of(0, 1));

            assertThat(result).extracting(Message::getId).containsExactly(m2.getId());
        }

        @Test
        @DisplayName("다른 채팅방의 메시지는 섞이지 않는다")
        void isolatesByRoom() {
            Message m1 = persist(message("1", "이 방 메시지"));
            Companion otherCompanion = persist(companionPost(persist(user("다른방장"))));
            ChatRoom otherRoom = persist(ChatRoom.create(otherCompanion));
            persist(Message.createGeneralMessage(otherRoom, sender, "2", "다른 방 메시지"));
            Message m3 = persist(message("3", "이 방 메시지2"));

            List<Message> result = messageRepository.findNewerByChatRoomIdWithSender(
                    chatRoom.getId(), m1.getId(), PageRequest.of(0, 20));

            assertThat(result).extracting(Message::getId).containsExactly(m3.getId());
        }
    }

    @Nested
    @DisplayName("findByChatRoomIdAndClientMessageId")
    class FindByChatRoomIdAndClientMessageId {

        @Test
        @DisplayName("존재하면 찾는다")
        void found() {
            Message message = persist(message("42", "멱등키로 조회"));

            Optional<Message> result = messageRepository.findByChatRoomIdAndClientMessageId(
                    chatRoom.getId(), "42");

            assertThat(result).get().extracting(Message::getId).isEqualTo(message.getId());
        }

        @Test
        @DisplayName("없으면 찾을 수 없다")
        void notFound() {
            assertThat(messageRepository.findByChatRoomIdAndClientMessageId(chatRoom.getId(), "42")).isEmpty();
        }

        @Test
        @DisplayName("다른 채팅방의 같은 clientMessageId는 찾지 않는다")
        void isolatesByRoom() {
            persist(message("42", "이 방 메시지"));
            Companion otherCompanion = persist(companionPost(persist(user("다른방장"))));
            ChatRoom otherRoom = persist(ChatRoom.create(otherCompanion));

            Optional<Message> result = messageRepository.findByChatRoomIdAndClientMessageId(
                    otherRoom.getId(), "42");

            assertThat(result).isEmpty();
        }
    }

    private Message message(String clientMessageId, String content) {
        return Message.createGeneralMessage(chatRoom, sender, clientMessageId, content);
    }

    private <T> T persist(T entity) {
        return entityManager.persistAndFlush(entity);
    }
}
