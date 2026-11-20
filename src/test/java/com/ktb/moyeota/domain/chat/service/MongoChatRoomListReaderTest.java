package com.ktb.moyeota.domain.chat.service;

import static com.ktb.moyeota.fixture.CompanionFixture.companionPost;
import static com.ktb.moyeota.fixture.CompanionFixture.taxiPot;
import static com.ktb.moyeota.fixture.UserFixture.user;
import static org.assertj.core.api.Assertions.assertThat;

import com.ktb.moyeota.domain.chat.document.ChatMessage;
import com.ktb.moyeota.domain.chat.dto.ChatRoomItem;
import com.ktb.moyeota.domain.chat.dto.ChatRoomListResponse;
import com.ktb.moyeota.domain.chat.entity.ChatRoom;
import com.ktb.moyeota.domain.chat.entity.CompanionParticipant;
import com.ktb.moyeota.domain.chat.repository.ChatMessageRepository;
import com.ktb.moyeota.domain.chat.support.ChatMongoScripts;
import com.ktb.moyeota.domain.companion.entity.Companion;
import com.ktb.moyeota.domain.companion.entity.CompanionKind;
import com.ktb.moyeota.domain.companion.entity.CompanionStatus;
import com.ktb.moyeota.domain.image.service.ImageUrlResolver;
import com.ktb.moyeota.domain.user.entity.User;
import com.ktb.moyeota.global.config.ClockConfig;
import com.ktb.moyeota.global.config.MongoTestContainerConfig;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.IntStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.data.mongodb.test.autoconfigure.AutoConfigureDataMongo;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.context.annotation.Import;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@DataJpaTest(properties = "moyeota.chat.message-store=mongo")
@AutoConfigureDataMongo
@Import({MongoChatRoomListReader.class, ChatMessageRepository.class, ChatRoomCursorCodec.class, ClockConfig.class,
        MongoTestContainerConfig.class})
class MongoChatRoomListReaderTest {

    private static final Instant BASE = Instant.parse("2026-11-20T01:00:00Z");

    @MockitoBean
    private ImageUrlResolver imageUrlResolver;

    @Autowired
    private MongoChatRoomListReader reader;

    @Autowired
    private ChatMessageRepository chatMessageRepository;

    @Autowired
    private TestEntityManager entityManager;

    @Autowired
    private MongoTemplate mongoTemplate;

    private User me;

    @BeforeEach
    void setUp() throws Exception {
        mongoTemplate.dropCollection(ChatMessage.class);
        ChatMongoScripts.applyIndexes();
        me = entityManager.persist(user("나"));
    }

    @Test
    @DisplayName("마지막 메시지가 최근인 방부터 주고, 메시지가 없는 방은 맨 뒤에 둔다")
    void ordersByLastMessageAt() {
        ChatRoom older = chatRoomWith(companionPost(me));
        ChatRoom empty = chatRoomWith(companionPost(me));
        ChatRoom newer = chatRoomWith(companionPost(me));
        appendAt(older, 10);
        appendAt(newer, 20);

        ChatRoomListResponse response = reader.findMyChatRooms(me.getId(), null, null);

        assertThat(roomIds(response)).containsExactly(newer.getId(), older.getId(), empty.getId());
        assertThat(response.nextCursor()).isNull();
    }

    @Test
    @DisplayName("방의 마지막 번호가 내 읽음 위치보다 크면 안 읽음이고, 메시지가 없으면 안 읽음이 아니다")
    void hasUnreadComparesLastSeqWithLastReadSeq() {
        ChatRoom unread = chatRoomWith(companionPost(me));
        ChatRoom allRead = chatRoomWith(companionPost(me));
        ChatRoom neverRead = chatRoomWith(companionPost(me));
        ChatRoom empty = chatRoomWith(companionPost(me));
        appendAt(unread, 1);
        appendAt(unread, 2);
        appendAt(allRead, 3);
        appendAt(neverRead, 4);
        markReadUpTo(unread, 1L);
        markReadUpTo(allRead, 1L);

        ChatRoomListResponse response = reader.findMyChatRooms(me.getId(), null, null);

        assertThat(unreadOf(response, unread)).isTrue();
        assertThat(unreadOf(response, allRead)).isFalse();
        assertThat(unreadOf(response, neverRead)).isTrue();
        assertThat(unreadOf(response, empty)).isFalse();
    }

    @Test
    @DisplayName("10개씩 나눠 주고, 다음 커서로 이어서 받으면 빠지거나 겹치는 방이 없다")
    void pagesWithCursor() {
        List<ChatRoom> rooms = new ArrayList<>();
        IntStream.rangeClosed(1, 12).forEach(i -> {
            ChatRoom room = chatRoomWith(companionPost(me));
            appendAt(room, i);
            rooms.add(room);
        });

        ChatRoomListResponse first = reader.findMyChatRooms(me.getId(), null, null);
        ChatRoomListResponse second = reader.findMyChatRooms(me.getId(), null, first.nextCursor());

        List<Long> expected = rooms.reversed().stream().map(ChatRoom::getId).toList();
        assertThat(roomIds(first)).containsExactlyElementsOf(expected.subList(0, 10));
        assertThat(first.nextCursor()).isNotNull();
        assertThat(roomIds(second)).containsExactlyElementsOf(expected.subList(10, 12));
        assertThat(second.nextCursor()).isNull();
    }

    @Test
    @DisplayName("종류를 주면 그 종류의 방만 준다")
    void filtersByKind() {
        ChatRoom post = chatRoomWith(companionPost(me));
        chatRoomWith(taxiPot(me, CompanionStatus.RECRUITING));

        ChatRoomListResponse response = reader.findMyChatRooms(me.getId(), CompanionKind.COMPANION, null);

        assertThat(roomIds(response)).containsExactly(post.getId());
    }

    private ChatRoom chatRoomWith(Companion companion) {
        entityManager.persist(companion);
        ChatRoom room = entityManager.persist(ChatRoom.create(companion));
        entityManager.persistAndFlush(CompanionParticipant.join(companion, me));
        return room;
    }

    private void appendAt(ChatRoom room, int seconds) {
        chatMessageRepository.append(ChatMessage.text(room.getId(), me.getId(), UUID.randomUUID().toString(),
                "메시지", BASE.plusSeconds(seconds)));
    }

    private void markReadUpTo(ChatRoom room, long seq) {
        entityManager.getEntityManager()
                .createQuery("""
                        update CompanionParticipant p set p.lastReadSeq = :seq
                         where p.companion = :companion and p.user.id = :userId
                        """)
                .setParameter("seq", seq)
                .setParameter("companion", room.getCompanion())
                .setParameter("userId", me.getId())
                .executeUpdate();
        entityManager.clear();
    }

    private static List<Long> roomIds(ChatRoomListResponse response) {
        return response.items().stream().map(ChatRoomItem::id).toList();
    }

    private static boolean unreadOf(ChatRoomListResponse response, ChatRoom room) {
        return response.items().stream()
                .filter(item -> item.id().equals(room.getId()))
                .findFirst()
                .orElseThrow()
                .hasUnread();
    }
}
