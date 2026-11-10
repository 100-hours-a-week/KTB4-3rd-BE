package com.ktb.moyeota.domain.settlement.service;

import static com.ktb.moyeota.domain.chat.entity.OutcomeStatus.COMPLETED;
import static com.ktb.moyeota.domain.chat.entity.OutcomeStatus.INCOMPLETE;
import static com.ktb.moyeota.domain.chat.entity.OutcomeStatus.PENDING;
import static com.ktb.moyeota.fixture.CompanionFixture.taxiPot;
import static com.ktb.moyeota.fixture.ParticipantFixture.participant;
import static com.ktb.moyeota.fixture.UserFixture.bankAccountHolder;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ktb.moyeota.domain.chat.entity.ChatRoom;
import com.ktb.moyeota.domain.chat.entity.CompanionParticipant;
import com.ktb.moyeota.domain.companion.entity.Companion;
import com.ktb.moyeota.domain.companion.entity.CompanionStatus;
import com.ktb.moyeota.domain.settlement.entity.Settlement;
import com.ktb.moyeota.domain.settlement.entity.SettlementParticipant;
import com.ktb.moyeota.domain.settlement.entity.SettlementStatus;
import com.ktb.moyeota.domain.settlement.entity.TransferAnalysisStatus;
import com.ktb.moyeota.domain.settlement.error.SettlementErrorCode;
import com.ktb.moyeota.domain.taxipot.error.TaxiPotErrorCode;
import com.ktb.moyeota.domain.taxipot.repository.TaxiPotParticipantRepository;
import com.ktb.moyeota.domain.user.entity.User;
import com.ktb.moyeota.global.exception.BusinessException;
import com.ktb.moyeota.global.exception.ErrorCode;
import com.ktb.moyeota.domain.notification.service.NotificationCommandService;
import java.time.LocalDateTime;
import java.util.List;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.util.ReflectionTestUtils;

@DataJpaTest
@Import(SettlementService.class)
class SettlementCompletionTest {

    @MockitoBean
    private NotificationCommandService notificationCommandService;

    private static final LocalDateTime RIDE_ENDED_AT = LocalDateTime.of(2026, 9, 5, 9, 30);

    @Autowired
    private SettlementService settlementService;

    @Autowired
    private TaxiPotParticipantRepository taxiPotParticipantRepository;

    @Autowired
    private TestEntityManager entityManager;

    private User host;
    private User rider;
    private Companion pot;
    private ChatRoom chatRoom;
    private Settlement settlement;

    @BeforeEach
    void setUp() {
        host = entityManager.persist(bankAccountHolder("방장"));
        rider = entityManager.persist(bankAccountHolder("동승자"));
        pot = entityManager.persist(taxiPot(host, CompanionStatus.COMPLETED, 2));
        chatRoom = entityManager.persist(ChatRoom.create(pot));
        entityManager.persist(participant(pot, host, PENDING));
        entityManager.persist(participant(pot, rider, PENDING));
        settlement = entityManager.persistAndFlush(Settlement.open(pot));
    }

    @Test
    @DisplayName("방장이 완료하면 정산이 완료 상태가 되고 완료 시각이 남는다")
    void completesSettlement() {
        requestPayment();

        settlementService.complete(host.getId(), settlement.getId());

        Settlement saved = reload(Settlement.class, settlement.getId());
        assertThat(saved.getStatus()).isEqualTo(SettlementStatus.COMPLETED);
        assertThat(saved.getCompletedAt()).isNotNull();
    }

    @Test
    @DisplayName("송금 인증을 하지 않은 동승자가 있어도 완료되고 모든 청구가 송금 완료로 확정된다")
    void confirmsAllTransfersRegardlessOfProof() {
        requestPayment();

        settlementService.complete(host.getId(), settlement.getId());

        assertThat(chargesOf(settlement)).singleElement().satisfies(charge -> {
            assertThat(charge.isTransferCompleted()).isTrue();
            assertThat(charge.getAnalysisStatus()).isEqualTo(TransferAnalysisStatus.PENDING);
        });
    }

    @Test
    @DisplayName("함께 탄 사람은 모두 완주하고 진행 중인 택시팟이 없어진다")
    void completesRiders() {
        requestPayment();

        settlementService.complete(host.getId(), settlement.getId());

        entityManager.flush();
        entityManager.clear();
        assertThat(participantsOf(pot)).allSatisfy(participation -> {
            assertThat(participation.getOutcomeStatus()).isEqualTo(COMPLETED);
            assertThat(participation.getLeftAt()).isNotNull();
        });
        assertThat(taxiPotParticipantRepository.findCurrentTaxiPot(rider.getId())).isEmpty();
        assertThat(taxiPotParticipantRepository.findCurrentTaxiPot(host.getId())).isEmpty();
    }

    @Test
    @DisplayName("채팅방은 바로 닫지 않는다")
    void keepsChatRoomOpen() {
        requestPayment();

        settlementService.complete(host.getId(), settlement.getId());

        assertThat(reload(ChatRoom.class, chatRoom.getId()).getClosedAt()).isNull();
    }

    @Test
    @DisplayName("운행 종료 때 이미 완주하고 방이 닫힌 정산은 그 기록을 덮어쓰지 않는다")
    void keepsEarlierCompletion() {
        ReflectionTestUtils.setField(chatRoom, "closedAt", RIDE_ENDED_AT);
        participantsOf(pot).forEach(participation -> {
            ReflectionTestUtils.setField(participation, "outcomeStatus", COMPLETED);
            ReflectionTestUtils.setField(participation, "leftAt", RIDE_ENDED_AT);
        });
        entityManager.flush();
        requestPayment();

        settlementService.complete(host.getId(), settlement.getId());

        entityManager.flush();
        entityManager.clear();
        assertThat(entityManager.find(ChatRoom.class, chatRoom.getId()).getClosedAt()).isEqualTo(RIDE_ENDED_AT);
        assertThat(participantsOf(pot)).extracting(CompanionParticipant::getLeftAt).containsOnly(RIDE_ENDED_AT);
    }

    @Test
    @DisplayName("운행 전에 나간 사람의 기록은 그대로 둔다")
    void keepsLeaver() {
        User leaver = entityManager.persist(bankAccountHolder("나간사람"));
        CompanionParticipant left = entityManager.persist(participant(pot, leaver, INCOMPLETE, RIDE_ENDED_AT));
        requestPayment();

        settlementService.complete(host.getId(), settlement.getId());

        CompanionParticipant saved = reload(CompanionParticipant.class, left.getId());
        assertThat(saved.getOutcomeStatus()).isEqualTo(INCOMPLETE);
        assertThat(saved.getLeftAt()).isEqualTo(RIDE_ENDED_AT);
    }

    @Test
    @DisplayName("금액이 정해지기 전이면 409 INVALID_STATE_TRANSITION이고 아무것도 바뀌지 않는다")
    void rejectsBeforeAmount() {
        assertErrorCode(() -> settlementService.complete(host.getId(), settlement.getId()),
                SettlementErrorCode.INVALID_STATE_TRANSITION);
        assertThat(reload(Settlement.class, settlement.getId()).getStatus()).isEqualTo(SettlementStatus.PENDING);
        assertThat(participantsOf(pot)).extracting(CompanionParticipant::getOutcomeStatus).containsOnly(PENDING);
        assertThat(reload(ChatRoom.class, chatRoom.getId()).getClosedAt()).isNull();
    }

    @Test
    @DisplayName("이미 완료한 정산이면 409 INVALID_STATE_TRANSITION이다")
    void rejectsSecondCompletion() {
        requestPayment();
        settlementService.complete(host.getId(), settlement.getId());

        assertErrorCode(() -> settlementService.complete(host.getId(), settlement.getId()),
                SettlementErrorCode.INVALID_STATE_TRANSITION);
    }

    @Test
    @DisplayName("함께 탄 동승자가 완료하면 403 HOST_ONLY이고 아무것도 바뀌지 않는다")
    void rejectsRider() {
        requestPayment();

        assertErrorCode(() -> settlementService.complete(rider.getId(), settlement.getId()),
                TaxiPotErrorCode.HOST_ONLY);
        assertThat(reload(Settlement.class, settlement.getId()).getStatus()).isEqualTo(SettlementStatus.REQUESTED);
    }

    @Test
    @DisplayName("함께 타지 않은 사람 · 없는 정산은 404 SETTLEMENT_NOT_FOUND다")
    void hiddenFromOthers() {
        User outsider = entityManager.persist(bankAccountHolder("외부인"));
        requestPayment();

        assertErrorCode(() -> settlementService.complete(outsider.getId(), settlement.getId()),
                SettlementErrorCode.SETTLEMENT_NOT_FOUND);
        assertErrorCode(() -> settlementService.complete(host.getId(), 999L),
                SettlementErrorCode.SETTLEMENT_NOT_FOUND);
    }

    private void requestPayment() {
        settlementService.requestManually(host.getId(), settlement.getId(), 18000);
        entityManager.flush();
        entityManager.clear();
    }

    private <T> T reload(Class<T> type, Long id) {
        entityManager.flush();
        entityManager.clear();
        return entityManager.find(type, id);
    }

    private List<CompanionParticipant> participantsOf(Companion companion) {
        return entityManager.getEntityManager()
                .createQuery("SELECT p FROM CompanionParticipant p WHERE p.companion.id = :id",
                        CompanionParticipant.class)
                .setParameter("id", companion.getId())
                .getResultList();
    }

    private List<SettlementParticipant> chargesOf(Settlement target) {
        entityManager.flush();
        entityManager.clear();
        return entityManager.getEntityManager()
                .createQuery("SELECT p FROM SettlementParticipant p WHERE p.settlement.id = :id",
                        SettlementParticipant.class)
                .setParameter("id", target.getId())
                .getResultList();
    }

    private static void assertErrorCode(ThrowingCallable call, ErrorCode expected) {
        assertThatThrownBy(call)
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(expected);
    }
}
