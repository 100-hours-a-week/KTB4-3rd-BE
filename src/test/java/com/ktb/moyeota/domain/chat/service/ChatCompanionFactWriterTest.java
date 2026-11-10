package com.ktb.moyeota.domain.chat.service;

import static com.ktb.moyeota.fixture.CompanionFixture.carpool;
import static com.ktb.moyeota.fixture.CompanionFixture.taxiPot;
import static com.ktb.moyeota.fixture.UserFixture.user;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;

import com.ktb.moyeota.domain.chat.entity.ChatRoom;
import com.ktb.moyeota.domain.chat.entity.Message;
import com.ktb.moyeota.domain.chat.entity.MessageType;
import com.ktb.moyeota.domain.companion.entity.Companion;
import com.ktb.moyeota.domain.companion.entity.CompanionKind;
import com.ktb.moyeota.domain.companion.entity.CompanionStatus;
import com.ktb.moyeota.domain.companion.message.CompanionChangedData;
import com.ktb.moyeota.domain.companion.message.CompanionChangedMessages;
import com.ktb.moyeota.domain.user.entity.User;
import com.ktb.moyeota.global.event.InvalidEventException;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.context.annotation.Import;

@DataJpaTest
@Import({ChatCompanionFactWriter.class, ChatSystemMessageService.class})
class ChatCompanionFactWriterTest {

    @Autowired
    private ChatCompanionFactWriter writer;

    @Autowired
    private TestEntityManager entityManager;

    private User host;

    @BeforeEach
    void setUp() {
        host = entityManager.persist(user("방장"));
    }

    @Test
    @DisplayName("택시팟 운행 종료는 운행 종료 · 미터기 사진 요청 메시지를 차례로 남기고 마지막 메시지를 갱신한다")
    void writesRideEndedAndMeterRequestForTaxiPot() {
        ChatRoom chatRoom = chatRoomOf(taxiPot(host, CompanionStatus.COMPLETED, 2));

        boolean written = writer.write(eventId(), CompanionChangedMessages.RIDE_ENDED,
                new CompanionChangedData(chatRoom.getCompanion().getId(), CompanionKind.TAXI_POT, null));

        assertThat(written).isTrue();
        List<Message> messages = messagesOf(chatRoom);
        assertThat(messages).extracting(Message::getMessageType)
                .containsExactly(MessageType.SYSTEM_RIDE_ENDED, MessageType.SYSTEM_METER_REQUESTED);
        assertThat(entityManager.find(ChatRoom.class, chatRoom.getId()).getLastMessageId())
                .isEqualTo(messages.get(1).getId());
    }

    @Test
    @DisplayName("카풀 운행 종료는 운행 종료 메시지만 남긴다")
    void writesOnlyRideEndedForCarpool() {
        ChatRoom chatRoom = chatRoomOf(carpool(host, CompanionStatus.COMPLETED, 2));

        writer.write(eventId(), CompanionChangedMessages.RIDE_ENDED,
                new CompanionChangedData(chatRoom.getCompanion().getId(), CompanionKind.CARPOOL, null));

        assertThat(messagesOf(chatRoom)).extracting(Message::getMessageType)
                .containsExactly(MessageType.SYSTEM_RIDE_ENDED);
    }

    @Test
    @DisplayName("같은 사실을 다시 받아도 메시지는 한 번만 남는다")
    void writesOnceForSameEvent() {
        ChatRoom chatRoom = chatRoomOf(taxiPot(host, CompanionStatus.IN_PROGRESS, 2));
        String eventId = eventId();
        CompanionChangedData data =
                new CompanionChangedData(chatRoom.getCompanion().getId(), CompanionKind.TAXI_POT, null);

        writer.write(eventId, CompanionChangedMessages.RIDE_STARTED, data);
        writer.write(eventId, CompanionChangedMessages.RIDE_STARTED, data);

        assertThat(messagesOf(chatRoom)).extracting(Message::getMessageType)
                .containsExactly(MessageType.SYSTEM_RIDE_STARTED);
    }

    @Test
    @DisplayName("사실이 다르면 같은 종류의 메시지라도 따로 남는다")
    void writesEachEvent() {
        ChatRoom chatRoom = chatRoomOf(taxiPot(host, CompanionStatus.RECRUITING, 3));
        User first = entityManager.persist(user("첫째"));
        User second = entityManager.persist(user("둘째"));

        writer.write(eventId(), CompanionChangedMessages.PARTICIPANT_JOINED,
                new CompanionChangedData(chatRoom.getCompanion().getId(), CompanionKind.TAXI_POT, first.getId()));
        writer.write(eventId(), CompanionChangedMessages.MATCHED,
                new CompanionChangedData(chatRoom.getCompanion().getId(), CompanionKind.TAXI_POT, second.getId()));

        assertThat(messagesOf(chatRoom)).extracting(Message::getMessageType, message -> message.getSender().getId())
                .containsExactly(
                        tuple(MessageType.SYSTEM_JOIN, first.getId()),
                        tuple(MessageType.SYSTEM_JOIN, second.getId()));
    }

    @Test
    @DisplayName("퇴장은 나간 사람을 보낸 사람으로 남긴다")
    void writesLeave() {
        ChatRoom chatRoom = chatRoomOf(taxiPot(host, CompanionStatus.RECRUITING, 2));
        User leaver = entityManager.persist(user("나간사람"));

        writer.write(eventId(), CompanionChangedMessages.PARTICIPANT_LEFT,
                new CompanionChangedData(chatRoom.getCompanion().getId(), CompanionKind.TAXI_POT, leaver.getId()));

        List<Message> messages = messagesOf(chatRoom);
        assertThat(messages).extracting(Message::getMessageType).containsExactly(MessageType.SYSTEM_LEAVE);
        assertThat(messages.get(0).getSender().getId()).isEqualTo(leaver.getId());
    }

    @Test
    @DisplayName("닫힌 채팅방에는 남기지 않는다")
    void skipsClosedChatRoom() {
        ChatRoom chatRoom = chatRoomOf(taxiPot(host, CompanionStatus.COMPLETED, 2));
        chatRoom.close();
        entityManager.flush();

        boolean written = writer.write(eventId(), CompanionChangedMessages.RIDE_ENDED,
                new CompanionChangedData(chatRoom.getCompanion().getId(), CompanionKind.TAXI_POT, null));

        assertThat(written).isFalse();
        assertThat(messagesOf(chatRoom)).isEmpty();
    }

    @Test
    @DisplayName("입장인데 user_id 가 없으면 잘못된 사실로 본다")
    void rejectsJoinWithoutUser() {
        ChatRoom chatRoom = chatRoomOf(taxiPot(host, CompanionStatus.RECRUITING, 2));
        CompanionChangedData data =
                new CompanionChangedData(chatRoom.getCompanion().getId(), CompanionKind.TAXI_POT, null);

        assertThatThrownBy(() -> writer.write(eventId(), CompanionChangedMessages.PARTICIPANT_JOINED, data))
                .isInstanceOf(InvalidEventException.class);
        assertThat(messagesOf(chatRoom)).isEmpty();
    }

    private ChatRoom chatRoomOf(Companion companion) {
        entityManager.persist(companion);
        return entityManager.persistAndFlush(ChatRoom.create(companion));
    }

    private List<Message> messagesOf(ChatRoom chatRoom) {
        entityManager.flush();
        return entityManager.getEntityManager()
                .createQuery("select m from Message m where m.chatRoom.id = :roomId order by m.id", Message.class)
                .setParameter("roomId", chatRoom.getId())
                .getResultList();
    }

    private static String eventId() {
        return UUID.randomUUID().toString();
    }
}
