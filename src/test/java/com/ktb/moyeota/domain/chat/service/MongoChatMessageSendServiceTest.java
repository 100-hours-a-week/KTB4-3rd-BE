package com.ktb.moyeota.domain.chat.service;

import static com.ktb.moyeota.fixture.CompanionFixture.companionPost;
import static com.ktb.moyeota.fixture.CompanionFixture.taxiPot;
import static com.ktb.moyeota.fixture.UserFixture.user;
import static org.assertj.core.api.Assertions.assertThat;

import com.ktb.moyeota.domain.chat.document.ChatMessage;
import com.ktb.moyeota.domain.chat.dto.ChatMessageItem;
import com.ktb.moyeota.domain.chat.dto.ChatMessageSendRequest;
import com.ktb.moyeota.domain.chat.entity.ChatRoom;
import com.ktb.moyeota.domain.chat.entity.CompanionParticipant;
import com.ktb.moyeota.domain.chat.entity.MessageType;
import com.ktb.moyeota.domain.chat.repository.ChatMessageRepository;
import com.ktb.moyeota.domain.chat.support.ChatMongoScripts;
import com.ktb.moyeota.domain.companion.entity.Companion;
import com.ktb.moyeota.domain.companion.entity.CompanionStatus;
import com.ktb.moyeota.domain.image.service.ImageUrlResolver;
import com.ktb.moyeota.domain.user.entity.User;
import com.ktb.moyeota.global.config.ClockConfig;
import com.ktb.moyeota.global.config.MongoTestContainerConfig;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.data.mongodb.test.autoconfigure.AutoConfigureDataMongo;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.context.annotation.Import;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@DataJpaTest(properties = "moyeota.chat.message-store=mongo")
@AutoConfigureDataMongo
@Import({MongoChatMessageSendService.class, ChatMessageRepository.class, ClockConfig.class,
        MongoTestContainerConfig.class})
class MongoChatMessageSendServiceTest {

    @MockitoBean
    private ImageUrlResolver imageUrlResolver;

    @Autowired
    private MongoChatMessageSendService service;

    @Autowired
    private TestEntityManager entityManager;

    @Autowired
    private MongoTemplate mongoTemplate;

    @BeforeEach
    void setUp() throws Exception {
        mongoTemplate.dropCollection(ChatMessage.class);
        ChatMongoScripts.applyIndexes();
    }

    @Test
    @DisplayName("참여자가 보낸 메시지를 MongoDB 에 방의 다음 번호로 저장하고 문자열 id 와 번호를 돌려준다")
    void savesToMongoWithNextSeq() {
        User sender = entityManager.persist(user("보낸사람"));
        ChatRoom chatRoom = chatRoomWith(companionPost(sender), sender);

        ChatMessageItem first = service.send(sender.getId(), chatRoom.getId(),
                new ChatMessageSendRequest("c-1", "안녕")).orElseThrow();
        ChatMessageItem second = service.send(sender.getId(), chatRoom.getId(),
                new ChatMessageSendRequest("c-2", "출발해요")).orElseThrow();

        assertThat(first.seq()).isEqualTo(1L);
        assertThat(second.seq()).isEqualTo(2L);
        assertThat(second.type()).isEqualTo(MessageType.TEXT);
        assertThat(second.content()).isEqualTo("출발해요");
        List<ChatMessage> stored = messagesOf(chatRoom);
        assertThat(stored).extracting(message -> message.getId().toString())
                .containsExactly(first.id(), second.id());
        assertThat(stored).extracting(ChatMessage::getSenderId).containsOnly(sender.getId());
    }

    @Test
    @DisplayName("택시팟은 보낸 사람을 이름으로, 동행모집은 닉네임으로 돌려준다")
    void returnsSenderNameByCompanionKind() {
        User sender = entityManager.persist(user("이름"));
        ChatRoom taxiRoom = chatRoomWith(taxiPot(sender, CompanionStatus.RECRUITING), sender);
        ChatRoom postRoom = chatRoomWith(companionPost(sender), sender);

        ChatMessageItem taxi = service.send(sender.getId(), taxiRoom.getId(),
                new ChatMessageSendRequest("t-1", "택시")).orElseThrow();
        ChatMessageItem post = service.send(sender.getId(), postRoom.getId(),
                new ChatMessageSendRequest("p-1", "동행")).orElseThrow();

        assertThat(taxi.sender().name()).isEqualTo(sender.getName());
        assertThat(taxi.sender().nickname()).isNull();
        assertThat(post.sender().nickname()).isEqualTo(sender.getNickname());
        assertThat(post.sender().name()).isNull();
    }

    @Test
    @DisplayName("같은 client_message_id 로 다시 보내면 처음 저장한 메시지를 돌려주고 한 건만 남는다")
    void returnsFirstMessageOnResend() {
        User sender = entityManager.persist(user("재전송"));
        ChatRoom chatRoom = chatRoomWith(companionPost(sender), sender);
        ChatMessageItem first = service.send(sender.getId(), chatRoom.getId(),
                new ChatMessageSendRequest("dup-1", "처음 보낸 내용")).orElseThrow();

        ChatMessageItem resent = service.send(sender.getId(), chatRoom.getId(),
                new ChatMessageSendRequest("dup-1", "다시 보낸 내용")).orElseThrow();

        assertThat(resent.id()).isEqualTo(first.id());
        assertThat(resent.seq()).isEqualTo(1L);
        assertThat(resent.content()).isEqualTo("처음 보낸 내용");
        assertThat(messagesOf(chatRoom)).hasSize(1);
    }

    @Test
    @DisplayName("참여 중이 아닌 사람이 보내면 저장하지 않는다")
    void ignoresNonParticipant() {
        User host = entityManager.persist(user("방장"));
        User stranger = entityManager.persist(user("외부인"));
        ChatRoom chatRoom = chatRoomWith(companionPost(host), host);

        Optional<ChatMessageItem> result = service.send(stranger.getId(), chatRoom.getId(),
                new ChatMessageSendRequest("x-1", "끼어들기"));

        assertThat(result).isEmpty();
        assertThat(messagesOf(chatRoom)).isEmpty();
    }

    private ChatRoom chatRoomWith(Companion companion, User participant) {
        entityManager.persist(companion);
        ChatRoom chatRoom = entityManager.persist(ChatRoom.create(companion));
        entityManager.persistAndFlush(CompanionParticipant.join(companion, participant));
        return chatRoom;
    }

    private List<ChatMessage> messagesOf(ChatRoom chatRoom) {
        Query query = new Query(Criteria.where("room_id").is(chatRoom.getId()));
        return mongoTemplate.find(query, ChatMessage.class);
    }
}
