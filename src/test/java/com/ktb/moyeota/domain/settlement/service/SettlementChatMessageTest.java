package com.ktb.moyeota.domain.settlement.service;

import static com.ktb.moyeota.domain.chat.entity.OutcomeStatus.PENDING;
import static com.ktb.moyeota.fixture.CompanionFixture.taxiPot;
import static com.ktb.moyeota.fixture.ParticipantFixture.participant;
import static com.ktb.moyeota.fixture.UserFixture.bankAccountHolder;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ktb.moyeota.domain.chat.dto.MessageItem;
import com.ktb.moyeota.domain.chat.entity.ChatRoom;
import com.ktb.moyeota.domain.chat.entity.Message;
import com.ktb.moyeota.domain.chat.entity.MessageType;
import com.ktb.moyeota.domain.chat.event.ChatMessageCreatedEvent;
import com.ktb.moyeota.domain.chat.service.ChatMessageService;
import com.ktb.moyeota.domain.chat.service.ChatSystemMessageService;
import com.ktb.moyeota.domain.chat.service.MessageCursorCodec;
import com.ktb.moyeota.domain.companion.entity.Companion;
import com.ktb.moyeota.domain.companion.entity.CompanionStatus;
import com.ktb.moyeota.domain.image.service.ImageUrlResolver;
import com.ktb.moyeota.domain.notification.service.NotificationCommandService;
import com.ktb.moyeota.domain.settlement.entity.Settlement;
import com.ktb.moyeota.domain.user.entity.User;
import com.ktb.moyeota.global.crypto.AccountNoCipher;
import com.ktb.moyeota.global.crypto.CryptoProperties;
import com.ktb.moyeota.global.exception.BusinessException;
import com.ktb.moyeota.global.external.s3.S3Properties;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;
import org.springframework.test.util.ReflectionTestUtils;

@DataJpaTest
@RecordApplicationEvents
@Import({SettlementService.class, SettlementChatMessagePublisher.class, SettlementSummaryReader.class,
        ChatSystemMessageService.class, ChatMessageService.class, MessageCursorCodec.class, ImageUrlResolver.class,
        AccountNoCipher.class})
@EnableConfigurationProperties({CryptoProperties.class, S3Properties.class})
class SettlementChatMessageTest {

    @Autowired
    private SettlementService settlementService;

    @Autowired
    private ChatMessageService chatMessageService;

    @Autowired
    private AccountNoCipher accountNoCipher;

    @Autowired
    private ApplicationEvents events;

    @Autowired
    private TestEntityManager entityManager;

    @MockitoBean
    private NotificationCommandService notificationCommandService;

    private User host;
    private User rider;
    private ChatRoom chatRoom;
    private Settlement settlement;

    @BeforeEach
    void setUp() {
        host = entityManager.persist(bankAccountHolder("방장", accountNoCipher.encrypt("11012345678")));
        rider = entityManager.persist(bankAccountHolder("동승자"));
        User otherRider = entityManager.persist(bankAccountHolder("동승자2"));
        Companion pot = entityManager.persist(taxiPot(host, CompanionStatus.COMPLETED, 3));
        chatRoom = entityManager.persist(ChatRoom.create(pot));
        entityManager.persist(participant(pot, host, PENDING));
        entityManager.persist(participant(pot, rider, PENDING));
        entityManager.persist(participant(pot, otherRider, PENDING));
        settlement = entityManager.persistAndFlush(Settlement.open(pot));
    }

    @Test
    @DisplayName("금액을 입력하면 채팅방에 보낸 사람 없는 송금 요청 메시지가 남는다")
    void leavesTransferRequestedMessage() {
        settlementService.requestManually(host.getId(), settlement.getId(), 26000);

        assertThat(messagesInRoom()).singleElement().satisfies(message -> {
            assertThat(message.getMessageType()).isEqualTo(MessageType.SYSTEM_TRANSFER_REQUESTED);
            assertThat(message.getContent()).isEqualTo("방장이 정산을 요청했어요");
            assertThat(message.getSender()).isNull();
        });
        assertThat(reloadChatRoom().getLastMessageId()).isEqualTo(messagesInRoom().getFirst().getId());
    }

    @Test
    @DisplayName("웹소켓으로 보내는 송금 요청 메시지에 총액 · 인당 금액 · 받는 사람 계좌가 실린다")
    void broadcastsSettlementSummary() {
        settlementService.requestManually(host.getId(), settlement.getId(), 26000);

        assertThat(events.stream(ChatMessageCreatedEvent.class))
                .singleElement()
                .satisfies(event -> {
                    assertThat(event.roomId()).isEqualTo(chatRoom.getId());
                    assertThat(event.message().type()).isEqualTo(MessageType.SYSTEM_TRANSFER_REQUESTED);
                    assertThat(event.message().settlement()).isEqualTo(expectedSummary());
                });
    }

    @Test
    @DisplayName("메시지 목록을 조회해도 송금 요청 메시지에 같은 정산 값이 붙고 다른 메시지에는 붙지 않는다")
    void listAttachesSettlementSummary() {
        settlementService.requestManually(host.getId(), settlement.getId(), 26000);
        flushAndClear();

        List<MessageItem> items = chatMessageService.findMessages(rider.getId(), chatRoom.getId(), null, null, null)
                .items();

        assertThat(items).singleElement().satisfies(item -> {
            assertThat(item.type()).isEqualTo(MessageType.SYSTEM_TRANSFER_REQUESTED);
            assertThat(item.settlement()).isEqualTo(expectedSummary());
        });
    }

    @Test
    @DisplayName("방장이 운행 뒤 계좌를 바꿔도 메시지에는 정산 때 계좌가 실린다")
    void keepsPayeeSnapshot() {
        settlementService.requestManually(host.getId(), settlement.getId(), 26000);
        entityManager.find(User.class, host.getId()).registerBankAccount("toss", accountNoCipher.encrypt("99999999999"));
        flushAndClear();

        MessageItem item = chatMessageService.findMessages(rider.getId(), chatRoom.getId(), null, null, null)
                .items().getFirst();

        assertThat(item.settlement().payee()).isEqualTo(new MessageItem.Payee(host.getId(), "홍길동", "kb", "11012345678"));
    }

    @Test
    @DisplayName("금액 입력이 거절되면 송금 요청 메시지가 더 남지 않는다")
    void noMessageWhenRejected() {
        settlementService.requestManually(host.getId(), settlement.getId(), 26000);

        assertThatThrownBy(() -> settlementService.requestManually(host.getId(), settlement.getId(), 30000))
                .isInstanceOf(BusinessException.class);
        assertThat(messagesInRoom()).hasSize(1);
    }

    @Test
    @DisplayName("정산을 완료하면 정산 완료 메시지가 남고 채팅방은 열어 둔다")
    void leavesSettlementCompletedMessage() {
        settlementService.requestManually(host.getId(), settlement.getId(), 26000);

        settlementService.complete(host.getId(), settlement.getId());

        assertThat(messagesInRoom())
                .extracting(Message::getMessageType)
                .containsExactly(MessageType.SYSTEM_TRANSFER_REQUESTED, MessageType.SYSTEM_SETTLEMENT_COMPLETED);
        assertThat(messagesInRoom().getLast().getContent()).isEqualTo("정산이 완료됐어요");
        assertThat(reloadChatRoom().getClosedAt()).isNull();
    }

    @Test
    @DisplayName("운행 종료 때 이미 닫힌 방에는 정산 메시지를 남기지 않는다")
    void noMessageInClosedRoom() {
        LocalDateTime closedAt = LocalDateTime.of(2026, 9, 5, 9, 30);
        ReflectionTestUtils.setField(chatRoom, "closedAt", closedAt);
        entityManager.flush();

        settlementService.requestManually(host.getId(), settlement.getId(), 26000);
        settlementService.complete(host.getId(), settlement.getId());

        assertThat(messagesInRoom()).isEmpty();
        assertThat(reloadChatRoom().getClosedAt()).isEqualTo(closedAt);
    }

    private MessageItem.Settlement expectedSummary() {
        return new MessageItem.Settlement(settlement.getId(), 26000, 8666,
                new MessageItem.Payee(host.getId(), "홍길동", "kb", "11012345678"));
    }

    private List<Message> messagesInRoom() {
        flushAndClear();
        return entityManager.getEntityManager()
                .createQuery("SELECT m FROM Message m WHERE m.chatRoom.id = :roomId ORDER BY m.id", Message.class)
                .setParameter("roomId", chatRoom.getId())
                .getResultList();
    }

    private ChatRoom reloadChatRoom() {
        flushAndClear();
        return entityManager.find(ChatRoom.class, chatRoom.getId());
    }

    private void flushAndClear() {
        entityManager.flush();
        entityManager.clear();
    }
}
