package com.ktb.moyeota.domain.chat.service;

import static com.ktb.moyeota.fixture.CompanionFixture.companionPost;
import static com.ktb.moyeota.fixture.CompanionFixture.taxiPot;
import static com.ktb.moyeota.fixture.UserFixture.user;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.ktb.moyeota.domain.chat.document.ChatMessage;
import com.ktb.moyeota.domain.chat.dto.ChatMessageItem;
import com.ktb.moyeota.domain.chat.dto.MessageCursor;
import com.ktb.moyeota.domain.chat.dto.MessageItem.Settlement;
import com.ktb.moyeota.domain.chat.dto.MessageListResponse;
import com.ktb.moyeota.domain.chat.entity.ChatRoom;
import com.ktb.moyeota.domain.chat.entity.CompanionParticipant;
import com.ktb.moyeota.domain.chat.entity.MessageType;
import com.ktb.moyeota.domain.chat.exception.ChatErrorCode;
import com.ktb.moyeota.domain.chat.repository.ChatMessageRepository;
import com.ktb.moyeota.domain.chat.support.ChatMongoScripts;
import com.ktb.moyeota.domain.companion.entity.Companion;
import com.ktb.moyeota.domain.companion.entity.CompanionStatus;
import com.ktb.moyeota.domain.image.service.ImageUrlResolver;
import com.ktb.moyeota.domain.settlement.service.SettlementSummaryReader;
import com.ktb.moyeota.domain.user.entity.User;
import com.ktb.moyeota.domain.user.repository.UserRepository;
import com.ktb.moyeota.global.config.ClockConfig;
import com.ktb.moyeota.global.config.MongoTestContainerConfig;
import com.ktb.moyeota.global.exception.BusinessException;
import com.ktb.moyeota.global.exception.CommonErrorCode;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.stream.IntStream;
import java.util.stream.LongStream;
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
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

@DataJpaTest(properties = "moyeota.chat.message-store=mongo")
@AutoConfigureDataMongo
@Import({MongoChatMessageService.class, ChatMessageRepository.class, MessageSeqCursorCodec.class,
        MessageCursorCodec.class, ClockConfig.class, MongoTestContainerConfig.class})
class MongoChatMessageServiceTest {

    private static final Instant BASE = Instant.parse("2026-11-12T01:00:00Z");

    @MockitoBean
    private ImageUrlResolver imageUrlResolver;

    @MockitoBean
    private SettlementSummaryReader settlementSummaryReader;

    @MockitoSpyBean
    private UserRepository userRepository;

    @Autowired
    private MongoChatMessageService service;

    @Autowired
    private ChatMessageRepository chatMessageRepository;

    @Autowired
    private MessageSeqCursorCodec cursorCodec;

    @Autowired
    private MessageCursorCodec v1CursorCodec;

    @Autowired
    private TestEntityManager entityManager;

    @Autowired
    private MongoTemplate mongoTemplate;

    private User me;
    private ChatRoom chatRoom;

    @BeforeEach
    void setUp() throws Exception {
        mongoTemplate.dropCollection(ChatMessage.class);
        ChatMongoScripts.applyIndexes();
        me = entityManager.persist(user("나"));
        chatRoom = chatRoomWith(companionPost(me), me);
    }

    @Test
    @DisplayName("처음 들어오면 최근 20개를 번호 큰 것부터 주고, 더 있으면 v2 이전 커서를 준다")
    void firstEntryReturnsLatestPage() {
        appendTexts(25);

        MessageListResponse<ChatMessageItem> response = service.findMessages(me.getId(), chatRoom.getId(),
                null, null, null);

        assertThat(seqs(response)).containsExactlyElementsOf(range(25, 6));
        assertThat(response.beforeCursor()).startsWith("v2.");
        assertThat(cursorCodec.decode(response.beforeCursor())).isEqualTo(6L);
        assertThat(response.afterCursor()).isNull();
        assertThat(response.items().get(0).id()).isNotBlank();
    }

    @Test
    @DisplayName("메시지가 한 페이지 이하면 이전 커서를 주지 않는다")
    void noBeforeCursorWhenSinglePage() {
        appendTexts(3);

        MessageListResponse<ChatMessageItem> response = service.findMessages(me.getId(), chatRoom.getId(),
                null, null, null);

        assertThat(seqs(response)).containsExactly(3L, 2L, 1L);
        assertThat(response.beforeCursor()).isNull();
    }

    @Test
    @DisplayName("이전 커서로 그보다 작은 번호를 읽고, 같이 보낸 이후 커서는 그대로 돌려준다")
    void readsOlderPage() {
        appendTexts(45);
        String after = cursorCodec.encode(45);

        MessageListResponse<ChatMessageItem> response = service.findMessages(me.getId(), chatRoom.getId(),
                "before", cursorCodec.encode(26), after);

        assertThat(seqs(response)).containsExactlyElementsOf(range(25, 6));
        assertThat(cursorCodec.decode(response.beforeCursor())).isEqualTo(6L);
        assertThat(response.afterCursor()).isEqualTo(after);
    }

    @Test
    @DisplayName("이후 커서로 그보다 큰 번호를 커서에 가까운 것부터 읽어 번호 큰 것부터 준다")
    void readsNewerPage() {
        appendTexts(45);

        MessageListResponse<ChatMessageItem> response = service.findMessages(me.getId(), chatRoom.getId(),
                "after", null, cursorCodec.encode(10));

        assertThat(seqs(response)).containsExactlyElementsOf(range(30, 11));
        assertThat(cursorCodec.decode(response.afterCursor())).isEqualTo(30L);
        assertThat(response.beforeCursor()).isNull();
    }

    @Test
    @DisplayName("마지막까지 읽으면 이후 커서를 주지 않는다")
    void noAfterCursorAtNewest() {
        appendTexts(5);

        MessageListResponse<ChatMessageItem> response = service.findMessages(me.getId(), chatRoom.getId(),
                "after", null, cursorCodec.encode(3));

        assertThat(seqs(response)).containsExactly(5L, 4L);
        assertThat(response.afterCursor()).isNull();
    }

    @Test
    @DisplayName("옛 v1 커서는 INVALID_CURSOR 로 거절한다")
    void rejectsV1Cursor() {
        appendTexts(3);
        String v1 = v1CursorCodec.encode(new MessageCursor(2L));

        assertThatThrownBy(() -> service.findMessages(me.getId(), chatRoom.getId(), "before", v1, null))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(CommonErrorCode.INVALID_CURSOR));
        assertThatThrownBy(() -> service.findMessages(me.getId(), chatRoom.getId(), "after", null, v1))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(CommonErrorCode.INVALID_CURSOR));
    }

    @Test
    @DisplayName("함께 보낸 반대쪽 커서가 v1 이어도 거절한다")
    void rejectsV1EchoCursor() {
        appendTexts(3);
        String v1 = v1CursorCodec.encode(new MessageCursor(2L));

        assertThatThrownBy(() -> service.findMessages(me.getId(), chatRoom.getId(),
                "before", cursorCodec.encode(3), v1))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(CommonErrorCode.INVALID_CURSOR));
    }

    @Test
    @DisplayName("방향이 있는데 커서가 없거나 방향 값이 틀리면 거절한다")
    void rejectsBadDirection() {
        assertThatThrownBy(() -> service.findMessages(me.getId(), chatRoom.getId(), "before", null, null))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(CommonErrorCode.INVALID_CURSOR));
        assertThatThrownBy(() -> service.findMessages(me.getId(), chatRoom.getId(), "sideways", null, null))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ChatErrorCode.INVALID_DIRECTION));
        assertThatThrownBy(() -> service.findMessages(me.getId(), chatRoom.getId(),
                null, cursorCodec.encode(1), null))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ChatErrorCode.INVALID_DIRECTION));
    }

    @Test
    @DisplayName("참여 중이 아닌 방은 CHATROOM_NOT_FOUND 로 거절한다")
    void rejectsNonParticipant() {
        User stranger = entityManager.persist(user("외부인"));

        assertThatThrownBy(() -> service.findMessages(stranger.getId(), chatRoom.getId(), null, null, null))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ChatErrorCode.CHATROOM_NOT_FOUND));
    }

    @Test
    @DisplayName("한 페이지의 보낸 사람 · 입장한 사람을 모아 users 를 한 번만 읽는다")
    void loadsUsersOnceForPage() {
        User other = entityManager.persist(user("상대"));
        entityManager.persistAndFlush(CompanionParticipant.join(chatRoom.getCompanion(), other));
        append(ChatMessage.system(chatRoom.getId(), MessageType.SYSTEM_JOIN, other.getId(), "join-1", null,
                BASE));
        append(ChatMessage.text(chatRoom.getId(), me.getId(), "m-1", "안녕", BASE.plusSeconds(1)));
        append(ChatMessage.text(chatRoom.getId(), other.getId(), "m-2", "반가워", BASE.plusSeconds(2)));
        append(ChatMessage.text(chatRoom.getId(), me.getId(), "m-3", "출발", BASE.plusSeconds(3)));
        given(imageUrlResolver.toUrl(any())).willReturn("https://img/profile.png");
        clearInvocations(userRepository);

        MessageListResponse<ChatMessageItem> response = service.findMessages(me.getId(), chatRoom.getId(),
                null, null, null);

        verify(userRepository, times(1)).findAllById(any());
        verify(userRepository, never()).findById(any());
        List<ChatMessageItem> items = response.items();
        assertThat(items).extracting(item -> item.sender() == null ? null : item.sender().nickname())
                .containsExactly(me.getNickname(), other.getNickname(), me.getNickname(), null);
        assertThat(items.get(0).sender().profileImageUrl()).isEqualTo("https://img/profile.png");
        assertThat(items.get(3).joiner().name()).isEqualTo(other.getName());
        assertThat(items.get(3).sender()).isNull();
    }

    @Test
    @DisplayName("택시팟 방은 보낸 사람을 이름으로 보여 준다")
    void taxiPotUsesRealName() {
        User host = entityManager.persist(user("택시방장"));
        ChatRoom taxiRoom = chatRoomWith(taxiPot(host, CompanionStatus.RECRUITING), host);
        append(ChatMessage.text(taxiRoom.getId(), host.getId(), "t-1", "택시", BASE));

        ChatMessageItem item = service.findMessages(host.getId(), taxiRoom.getId(), null, null, null)
                .items().get(0);

        assertThat(item.sender().name()).isEqualTo(host.getName());
        assertThat(item.sender().nickname()).isNull();
    }

    @Test
    @DisplayName("송금 요청 카드가 있으면 응답할 때 정산 값을 붙이고, 없으면 정산을 읽지 않는다")
    void attachesSettlementOnTransferCard() {
        Settlement settlement = new Settlement(9L, 12000, 4000, null);
        given(settlementSummaryReader.findByCompanionId(chatRoom.getCompanion().getId()))
                .willReturn(Optional.of(settlement));
        append(ChatMessage.text(chatRoom.getId(), me.getId(), "m-1", "정산해요", BASE));

        service.findMessages(me.getId(), chatRoom.getId(), null, null, null);
        verify(settlementSummaryReader, never()).findByCompanionId(any());

        append(ChatMessage.system(chatRoom.getId(), MessageType.SYSTEM_TRANSFER_REQUESTED, null, "transfer-1",
                "송금해 주세요", BASE.plusSeconds(1)));

        List<ChatMessageItem> items = service.findMessages(me.getId(), chatRoom.getId(), null, null, null)
                .items();

        assertThat(items.get(0).type()).isEqualTo(MessageType.SYSTEM_TRANSFER_REQUESTED);
        assertThat(items.get(0).settlement()).isEqualTo(settlement);
        assertThat(items.get(1).settlement()).isNull();
        verify(settlementSummaryReader, times(1)).findByCompanionId(chatRoom.getCompanion().getId());
    }

    @Test
    @DisplayName("다른 방의 메시지는 섞이지 않는다")
    void isolatesRooms() {
        User otherHost = entityManager.persist(user("다른방장"));
        ChatRoom otherRoom = chatRoomWith(companionPost(otherHost), otherHost);
        appendTexts(2);
        append(ChatMessage.text(otherRoom.getId(), otherHost.getId(), "o-1", "다른 방", BASE));

        MessageListResponse<ChatMessageItem> response = service.findMessages(me.getId(), chatRoom.getId(),
                null, null, null);

        assertThat(response.items()).hasSize(2)
                .allSatisfy(item -> assertThat(item.content()).startsWith("메시지"));
    }

    private void appendTexts(int count) {
        IntStream.rangeClosed(1, count).forEach(i -> append(ChatMessage.text(chatRoom.getId(), me.getId(),
                "c-" + i, "메시지 " + i, BASE.plusSeconds(i))));
    }

    private void append(ChatMessage message) {
        chatMessageRepository.append(message);
    }

    private ChatRoom chatRoomWith(Companion companion, User participant) {
        entityManager.persist(companion);
        ChatRoom room = entityManager.persist(ChatRoom.create(companion));
        entityManager.persistAndFlush(CompanionParticipant.join(companion, participant));
        return room;
    }

    private static List<Long> seqs(MessageListResponse<ChatMessageItem> response) {
        return response.items().stream().map(ChatMessageItem::seq).toList();
    }

    private static List<Long> range(long fromInclusive, long toInclusive) {
        return LongStream.iterate(fromInclusive, seq -> seq >= toInclusive, seq -> seq - 1).boxed().toList();
    }
}
