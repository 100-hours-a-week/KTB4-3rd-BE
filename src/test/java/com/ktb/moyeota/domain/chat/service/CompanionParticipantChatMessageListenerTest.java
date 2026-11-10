package com.ktb.moyeota.domain.chat.service;

import static com.ktb.moyeota.fixture.CompanionFixture.companionPost;
import static com.ktb.moyeota.fixture.UserFixture.user;
import static org.assertj.core.api.Assertions.assertThat;

import com.ktb.moyeota.domain.chat.entity.ChatRoom;
import com.ktb.moyeota.domain.chat.entity.Message;
import com.ktb.moyeota.domain.chat.entity.MessageType;
import com.ktb.moyeota.domain.companion.entity.Companion;
import com.ktb.moyeota.domain.companion.entity.CompanionKind;
import com.ktb.moyeota.domain.companion.event.CompanionParticipantJoinedEvent;
import com.ktb.moyeota.domain.companion.event.CompanionParticipantLeftEvent;
import com.ktb.moyeota.domain.user.entity.User;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Import;

@DataJpaTest
@Import({CompanionParticipantChatMessageListener.class, ChatSystemMessageService.class})
class CompanionParticipantChatMessageListenerTest {

    @Autowired
    private ApplicationEventPublisher eventPublisher;

    @Autowired
    private TestEntityManager entityManager;

    private User member;
    private ChatRoom chatRoom;

    @BeforeEach
    void setUp() {
        User host = entityManager.persist(user("방장"));
        member = entityManager.persist(user("참여자"));
        Companion companion = entityManager.persist(companionPost(host));
        chatRoom = entityManager.persistAndFlush(ChatRoom.create(companion));
    }

    @Test
    @DisplayName("입장 · 퇴장 사실을 받으면 그 사람의 입장 · 퇴장 메시지를 남긴다")
    void writesJoinAndLeave() {
        eventPublisher.publishEvent(new CompanionParticipantJoinedEvent(companionId(), CompanionKind.COMPANION,
                member.getId()));
        eventPublisher.publishEvent(new CompanionParticipantLeftEvent(companionId(), CompanionKind.COMPANION,
                member.getId()));

        assertThat(messages()).extracting(Message::getMessageType)
                .containsExactly(MessageType.SYSTEM_JOIN, MessageType.SYSTEM_LEAVE);
        assertThat(messages()).allSatisfy(message -> assertThat(message.getSender().getId()).isEqualTo(member.getId()));
    }

    @Test
    @DisplayName("채팅방이 이미 닫혔으면 퇴장 메시지를 남기지 않는다")
    void skipsClosedChatRoom() {
        chatRoom.close();
        entityManager.flush();

        eventPublisher.publishEvent(new CompanionParticipantLeftEvent(companionId(), CompanionKind.COMPANION,
                member.getId()));

        assertThat(messages()).isEmpty();
    }

    private Long companionId() {
        return chatRoom.getCompanion().getId();
    }

    private List<Message> messages() {
        entityManager.flush();
        return entityManager.getEntityManager()
                .createQuery("select m from Message m where m.chatRoom.id = :roomId order by m.id", Message.class)
                .setParameter("roomId", chatRoom.getId())
                .getResultList();
    }
}
