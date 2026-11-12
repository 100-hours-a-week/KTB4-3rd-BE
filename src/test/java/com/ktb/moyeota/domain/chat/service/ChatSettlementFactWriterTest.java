package com.ktb.moyeota.domain.chat.service;

import static com.ktb.moyeota.domain.chat.entity.OutcomeStatus.PENDING;
import static com.ktb.moyeota.fixture.CompanionFixture.taxiPot;
import static com.ktb.moyeota.fixture.ParticipantFixture.participant;
import static com.ktb.moyeota.fixture.UserFixture.bankAccountHolder;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;

import com.ktb.moyeota.domain.chat.dto.MessageItem;
import com.ktb.moyeota.domain.chat.entity.ChatRoom;
import com.ktb.moyeota.domain.chat.entity.Message;
import com.ktb.moyeota.domain.chat.entity.MessageType;
import com.ktb.moyeota.domain.chat.event.ChatMessageCreatedEvent;
import com.ktb.moyeota.domain.companion.entity.Companion;
import com.ktb.moyeota.domain.companion.entity.CompanionStatus;
import com.ktb.moyeota.domain.settlement.entity.Settlement;
import com.ktb.moyeota.domain.settlement.entity.SettlementParticipant;
import com.ktb.moyeota.domain.settlement.message.SettlementChangedData;
import com.ktb.moyeota.domain.settlement.message.SettlementChangedMessages;
import com.ktb.moyeota.domain.settlement.service.SettlementSummaryReader;
import com.ktb.moyeota.domain.user.entity.User;
import com.ktb.moyeota.global.crypto.AccountNoCipher;
import com.ktb.moyeota.global.crypto.CryptoProperties;
import com.ktb.moyeota.global.event.InvalidEventException;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;

@DataJpaTest
@RecordApplicationEvents
@Import({ChatSettlementFactWriter.class, ChatSystemMessageService.class, MysqlSystemMessageWriter.class, SettlementSummaryReader.class,
        AccountNoCipher.class})
@EnableConfigurationProperties(CryptoProperties.class)
class ChatSettlementFactWriterTest {

    @Autowired
    private ChatSettlementFactWriter writer;

    @Autowired
    private AccountNoCipher accountNoCipher;

    @Autowired
    private ApplicationEvents events;

    @Autowired
    private TestEntityManager entityManager;

    private User host;
    private User rider;
    private ChatRoom chatRoom;
    private Settlement settlement;

    @BeforeEach
    void setUp() {
        host = entityManager.persist(bankAccountHolder("방장", accountNoCipher.encrypt("11012345678")));
        rider = entityManager.persist(bankAccountHolder("동승자"));
        Companion pot = entityManager.persist(taxiPot(host, CompanionStatus.COMPLETED, 2));
        chatRoom = entityManager.persist(ChatRoom.create(pot));
        entityManager.persist(participant(pot, host, PENDING));
        entityManager.persist(participant(pot, rider, PENDING));
        settlement = entityManager.persistAndFlush(Settlement.open(pot));
    }

    @Test
    @DisplayName("송금 요청 사실은 보낸 사람 없는 송금 요청 메시지를 남기고, 전달하는 메시지에 정산 값을 싣는다")
    void writesTransferRequested() {
        settlement.requestManually(26000);
        entityManager.flush();

        boolean written = writer.write(eventId(), SettlementChangedMessages.TRANSFER_REQUESTED, data());

        assertThat(written).isTrue();
        assertThat(messagesInRoom()).singleElement().satisfies(message -> {
            assertThat(message.getMessageType()).isEqualTo(MessageType.SYSTEM_TRANSFER_REQUESTED);
            assertThat(message.getContent()).isEqualTo("방장이 정산을 요청했어요");
            assertThat(message.getSender()).isNull();
        });
        assertThat(events.stream(ChatMessageCreatedEvent.class)).singleElement()
                .satisfies(event -> assertThat(event.message().settlement()).isEqualTo(
                        new MessageItem.Settlement(settlement.getId(), 26000, 13000,
                                new MessageItem.Payee(host.getId(), "홍길동", "kb", "11012345678"))));
    }

    @Test
    @DisplayName("송금 확인 어려움 · 정산 완료 사실은 각자의 안내 메시지를 남긴다")
    void writesExhaustedAndCompleted() {
        writer.write(eventId(), SettlementChangedMessages.TRANSFER_EXHAUSTED, data());
        writer.write(eventId(), SettlementChangedMessages.COMPLETED, data());

        assertThat(messagesInRoom()).extracting(Message::getMessageType, Message::getContent).containsExactly(
                tuple(MessageType.SYSTEM_TRANSFER_UNVERIFIED,
                        "송금 확인이 어려워요. 대면으로 송금 내역을 확인한 뒤 방장이 정산을 완료해주세요"),
                tuple(MessageType.SYSTEM_SETTLEMENT_COMPLETED, "정산이 완료됐어요"));
    }

    @Test
    @DisplayName("송금 완료 사실은 송금한 사람만 남기고, 전달하는 메시지에 송금한 사람 id · 이름 · 청구 금액을 싣는다")
    void writesTransferDone() {
        settlement.requestManually(26000);
        entityManager.persist(SettlementParticipant.charge(settlement, rider, 13000));
        entityManager.flush();

        writer.write(eventId(), SettlementChangedMessages.TRANSFER_DONE,
                new SettlementChangedData(settlement.getId(), chatRoom.getCompanion().getId(), rider.getId()));

        assertThat(messagesInRoom()).singleElement().satisfies(message -> {
            assertThat(message.getMessageType()).isEqualTo(MessageType.SYSTEM_TRANSFER_DONE);
            assertThat(message.getContent()).isNull();
            assertThat(message.getSender().getId()).isEqualTo(rider.getId());
        });
        assertThat(events.stream(ChatMessageCreatedEvent.class)).singleElement()
                .extracting(ChatMessageCreatedEvent::message)
                .extracting(MessageItem::transferrer, MessageItem::amount, MessageItem::content)
                .containsExactly(new MessageItem.SystemActor(rider.getId(), rider.getName()), 13000, null);
    }

    @Test
    @DisplayName("송금한 사람의 청구가 없으면 남기지 않고 실패한다")
    void failsTransferDoneWithoutCharge() {
        assertThatThrownBy(() -> writer.write(eventId(), SettlementChangedMessages.TRANSFER_DONE,
                new SettlementChangedData(settlement.getId(), chatRoom.getCompanion().getId(), rider.getId())))
                .isInstanceOf(IllegalStateException.class);
        assertThat(messagesInRoom()).isEmpty();
    }

    @Test
    @DisplayName("방장 확인 요청 사실은 송금 도착 확인 안내 메시지를 남긴다")
    void writesConfirmRequested() {
        writer.write(eventId(), SettlementChangedMessages.CONFIRM_REQUESTED, data());

        assertThat(messagesInRoom()).extracting(Message::getMessageType, Message::getContent).containsExactly(
                tuple(MessageType.SYSTEM_SETTLEMENT_CONFIRM_REQUESTED, "모두의 송금이 정상적으로 도착했나요?"));
    }

    @Test
    @DisplayName("송금한 사람이 없는 송금 완료 사실은 남기지 않고 실패한다")
    void failsTransferDoneWithoutUser() {
        settlement.requestManually(26000);
        entityManager.flush();

        assertThatThrownBy(() -> writer.write(eventId(), SettlementChangedMessages.TRANSFER_DONE, data()))
                .isInstanceOf(InvalidEventException.class);
        assertThat(messagesInRoom()).isEmpty();
    }

    @Test
    @DisplayName("같은 사실을 다시 받아도 메시지는 한 번만 남는다")
    void writesOnceForSameEvent() {
        String eventId = eventId();

        writer.write(eventId, SettlementChangedMessages.COMPLETED, data());
        writer.write(eventId, SettlementChangedMessages.COMPLETED, data());

        assertThat(messagesInRoom()).extracting(Message::getMessageType)
                .containsExactly(MessageType.SYSTEM_SETTLEMENT_COMPLETED);
    }

    @Test
    @DisplayName("닫힌 채팅방에는 남기지 않는다")
    void skipsClosedChatRoom() {
        chatRoom.close();
        entityManager.flush();

        boolean written = writer.write(eventId(), SettlementChangedMessages.COMPLETED, data());

        assertThat(written).isFalse();
        assertThat(messagesInRoom()).isEmpty();
    }

    @Test
    @DisplayName("금액이 정해지지 않은 정산의 송금 요청은 남기지 않고 실패한다")
    void failsTransferRequestedWithoutAmount() {
        assertThatThrownBy(() -> writer.write(eventId(), SettlementChangedMessages.TRANSFER_REQUESTED, data()))
                .isInstanceOf(IllegalStateException.class);
        assertThat(messagesInRoom()).isEmpty();
    }

    private SettlementChangedData data() {
        return new SettlementChangedData(settlement.getId(), chatRoom.getCompanion().getId());
    }

    private List<Message> messagesInRoom() {
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
