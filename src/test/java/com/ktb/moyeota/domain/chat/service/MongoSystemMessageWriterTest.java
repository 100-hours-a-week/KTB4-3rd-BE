package com.ktb.moyeota.domain.chat.service;

import static com.ktb.moyeota.fixture.CompanionFixture.carpool;
import static com.ktb.moyeota.fixture.CompanionFixture.taxiPot;
import static com.ktb.moyeota.fixture.UserFixture.user;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import com.ktb.moyeota.domain.chat.document.ChatMessage;
import com.ktb.moyeota.domain.chat.dto.ChatMessageItem;
import com.ktb.moyeota.domain.chat.dto.MessageItem.Payee;
import com.ktb.moyeota.domain.chat.dto.MessageItem.Settlement;
import com.ktb.moyeota.domain.chat.entity.ChatRoom;
import com.ktb.moyeota.domain.chat.entity.MessageType;
import com.ktb.moyeota.domain.chat.event.ChatMessageAppendedEvent;
import com.ktb.moyeota.domain.chat.repository.ChatMessageRepository;
import com.ktb.moyeota.domain.chat.support.ChatMongoScripts;
import com.ktb.moyeota.domain.companion.entity.Companion;
import com.ktb.moyeota.domain.companion.entity.CompanionKind;
import com.ktb.moyeota.domain.companion.entity.CompanionStatus;
import com.ktb.moyeota.domain.companion.message.CompanionChangedData;
import com.ktb.moyeota.domain.companion.message.CompanionChangedMessages;
import com.ktb.moyeota.domain.user.entity.User;
import com.ktb.moyeota.global.config.ClockConfig;
import com.ktb.moyeota.global.config.MongoTestContainerConfig;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.data.mongodb.test.autoconfigure.AutoConfigureDataMongo;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;

@DataJpaTest(properties = "moyeota.chat.message-store=mongo")
@AutoConfigureDataMongo
@RecordApplicationEvents
@Import({ChatCompanionFactWriter.class, MongoSystemMessageWriter.class, ChatMessageRepository.class,
        ClockConfig.class, MongoTestContainerConfig.class})
class MongoSystemMessageWriterTest {

    @Autowired
    private ChatCompanionFactWriter companionFactWriter;

    @Autowired
    private MongoSystemMessageWriter systemMessageWriter;

    @Autowired
    private TestEntityManager entityManager;

    @Autowired
    private MongoTemplate mongoTemplate;

    @Autowired
    private ApplicationEvents events;

    private User host;

    @BeforeEach
    void setUp() throws Exception {
        mongoTemplate.dropCollection(ChatMessage.class);
        ChatMongoScripts.applyIndexes();
        host = entityManager.persist(user("방장"));
    }

    @Test
    @DisplayName("택시팟 운행 종료 사실은 운행 종료 · 미터기 사진 요청을 MongoDB 에 차례 번호로 남기고 각각 방에 전달한다")
    void appendsRideEndedAndMeterRequestInOrder() {
        ChatRoom chatRoom = chatRoomOf(taxiPot(host, CompanionStatus.COMPLETED, 2));

        companionFactWriter.write(eventId(), CompanionChangedMessages.RIDE_ENDED,
                new CompanionChangedData(chatRoom.getCompanion().getId(), CompanionKind.TAXI_POT, null));

        assertThat(messagesOf(chatRoom)).extracting(ChatMessage::getSeq, ChatMessage::getType, ChatMessage::getContent)
                .containsExactly(
                        tuple(1L, MessageType.SYSTEM_RIDE_ENDED, null),
                        tuple(2L, MessageType.SYSTEM_METER_REQUESTED, "택시 미터기 사진을 보내주세요"));
        assertThat(appended()).extracting(ChatMessageItem::seq, ChatMessageItem::type)
                .containsExactly(
                        tuple(1L, MessageType.SYSTEM_RIDE_ENDED),
                        tuple(2L, MessageType.SYSTEM_METER_REQUESTED));
    }

    @Test
    @DisplayName("같은 사실을 다시 받아도 메시지는 한 번만 남고 방에도 한 번만 전달한다")
    void appendsOnceForSameFact() {
        ChatRoom chatRoom = chatRoomOf(carpool(host, CompanionStatus.IN_PROGRESS, 2));
        String eventId = eventId();
        CompanionChangedData data =
                new CompanionChangedData(chatRoom.getCompanion().getId(), CompanionKind.CARPOOL, null);

        companionFactWriter.write(eventId, CompanionChangedMessages.RIDE_STARTED, data);
        companionFactWriter.write(eventId, CompanionChangedMessages.RIDE_STARTED, data);

        assertThat(messagesOf(chatRoom)).extracting(ChatMessage::getType)
                .containsExactly(MessageType.SYSTEM_RIDE_STARTED);
        assertThat(appended()).hasSize(1);
    }

    @Test
    @DisplayName("입장 · 퇴장은 그 사람을 sender_id 로 저장하고 응답에는 입장한 사람 · 나간 사람으로 담는다")
    void storesActorOfJoinAndLeave() {
        ChatRoom chatRoom = chatRoomOf(taxiPot(host, CompanionStatus.RECRUITING, 2));
        User member = entityManager.persist(user("참여자"));
        Long companionId = chatRoom.getCompanion().getId();

        companionFactWriter.write(eventId(), CompanionChangedMessages.PARTICIPANT_JOINED,
                new CompanionChangedData(companionId, CompanionKind.TAXI_POT, member.getId()));
        companionFactWriter.write(eventId(), CompanionChangedMessages.PARTICIPANT_LEFT,
                new CompanionChangedData(companionId, CompanionKind.TAXI_POT, member.getId()));

        assertThat(messagesOf(chatRoom)).extracting(ChatMessage::getType, ChatMessage::getSenderId)
                .containsExactly(
                        tuple(MessageType.SYSTEM_JOIN, member.getId()),
                        tuple(MessageType.SYSTEM_LEAVE, member.getId()));
        List<ChatMessageItem> items = appended();
        assertThat(items.get(0).joiner().id()).isEqualTo(member.getId());
        assertThat(items.get(0).leaver()).isNull();
        assertThat(items.get(1).leaver().name()).isEqualTo(member.getName());
        assertThat(items.get(1).joiner()).isNull();
    }

    @Test
    @DisplayName("닫힌 채팅방에는 남기지 않는다")
    void skipsClosedChatRoom() {
        ChatRoom chatRoom = chatRoomOf(taxiPot(host, CompanionStatus.COMPLETED, 2));
        chatRoom.close();
        entityManager.flush();

        boolean written = companionFactWriter.write(eventId(), CompanionChangedMessages.RIDE_ENDED,
                new CompanionChangedData(chatRoom.getCompanion().getId(), CompanionKind.TAXI_POT, null));

        assertThat(written).isFalse();
        assertThat(messagesOf(chatRoom)).isEmpty();
        assertThat(appended()).isEmpty();
    }

    @Test
    @DisplayName("정산 요청은 문구만 저장하고 정산 카드 값은 방에 전달하는 응답에만 붙인다")
    void attachesSettlementOnlyToItem() {
        ChatRoom chatRoom = chatRoomOf(taxiPot(host, CompanionStatus.COMPLETED, 2));
        Settlement settlement = new Settlement(31L, 12_000, 6_000, new Payee(host.getId(), "방장", "국민", "123"));

        systemMessageWriter.requestTransfer(chatRoom, settlement, UUID.randomUUID().toString());

        assertThat(messagesOf(chatRoom)).singleElement().satisfies(message -> {
            assertThat(message.getType()).isEqualTo(MessageType.SYSTEM_TRANSFER_REQUESTED);
            assertThat(message.getContent()).isEqualTo("방장이 정산을 요청했어요");
        });
        assertThat(appended()).singleElement()
                .satisfies(item -> assertThat(item.settlement()).isEqualTo(settlement));
    }

    private ChatRoom chatRoomOf(Companion companion) {
        entityManager.persist(companion);
        return entityManager.persistAndFlush(ChatRoom.create(companion));
    }

    private List<ChatMessage> messagesOf(ChatRoom chatRoom) {
        Query query = new Query(Criteria.where("room_id").is(chatRoom.getId())).with(Sort.by("seq"));
        return mongoTemplate.find(query, ChatMessage.class);
    }

    private List<ChatMessageItem> appended() {
        return events.stream(ChatMessageAppendedEvent.class).map(ChatMessageAppendedEvent::message).toList();
    }

    private static String eventId() {
        return UUID.randomUUID().toString();
    }
}
