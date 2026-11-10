package com.ktb.moyeota.domain.settlement.service;

import static com.ktb.moyeota.domain.chat.entity.OutcomeStatus.COMPLETED;
import static com.ktb.moyeota.domain.chat.entity.OutcomeStatus.INCOMPLETE;
import static com.ktb.moyeota.fixture.CompanionFixture.taxiPot;
import static com.ktb.moyeota.fixture.ParticipantFixture.participant;
import static com.ktb.moyeota.fixture.UserFixture.bankAccountHolder;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.ktb.moyeota.domain.chat.entity.ChatRoom;
import com.ktb.moyeota.domain.chat.entity.CompanionParticipant;
import com.ktb.moyeota.domain.chat.entity.OutcomeStatus;
import com.ktb.moyeota.domain.companion.entity.Companion;
import com.ktb.moyeota.domain.companion.entity.CompanionStatus;
import com.ktb.moyeota.domain.notification.entity.NotificationType;
import com.ktb.moyeota.domain.settlement.entity.MeterAnalysisStatus;
import com.ktb.moyeota.domain.settlement.entity.Settlement;
import com.ktb.moyeota.domain.settlement.entity.SettlementParticipant;
import com.ktb.moyeota.domain.settlement.entity.SettlementStatus;
import com.ktb.moyeota.domain.settlement.entity.TransferAnalysisStatus;
import com.ktb.moyeota.domain.settlement.error.SettlementErrorCode;
import com.ktb.moyeota.domain.settlement.model.RequestedSettlement;
import com.ktb.moyeota.domain.taxipot.error.TaxiPotErrorCode;
import com.ktb.moyeota.domain.user.entity.User;
import com.ktb.moyeota.global.exception.BusinessException;
import com.ktb.moyeota.global.exception.ErrorCode;
import com.ktb.moyeota.domain.notification.service.NotificationCommandService;
import java.util.List;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.util.ReflectionTestUtils;

@DataJpaTest
@Import(SettlementService.class)
class SettlementManualRequestTest {

    @MockitoBean
    private NotificationCommandService notificationCommandService;

    @Autowired
    private SettlementService settlementService;

    @Autowired
    private TestEntityManager entityManager;

    private User host;
    private User rider1;
    private User rider2;
    private Companion pot;
    private List<CompanionParticipant> riderParticipations;

    @BeforeEach
    void setUp() {
        host = entityManager.persist(bankAccountHolder("방장"));
        rider1 = entityManager.persist(bankAccountHolder("동승자1"));
        rider2 = entityManager.persist(bankAccountHolder("동승자2"));
        pot = entityManager.persist(taxiPot(host, CompanionStatus.COMPLETED, 3));
        entityManager.persist(ChatRoom.create(pot));
        riderParticipations = List.of(
                persistParticipant(host, COMPLETED),
                persistParticipant(rider1, COMPLETED),
                persistParticipant(rider2, COMPLETED));
    }

    @Test
    @DisplayName("방장이 금액을 입력하면 수동 입력으로 청구 단계가 되고 응답에 인당 금액이 담긴다")
    void requestsPayment() {
        Settlement settlement = persistSettlement();

        RequestedSettlement requested = settlementService.requestManually(host.getId(), settlement.getId(), 18000);

        assertThat(requested).isEqualTo(
                new RequestedSettlement(3, 6000, SettlementStatus.REQUESTED, MeterAnalysisStatus.MANUAL));
        Settlement saved = reload(settlement);
        assertThat(saved.getTotalAmount()).isEqualTo(18000);
        assertThat(saved.getStatus()).isEqualTo(SettlementStatus.REQUESTED);
        assertThat(saved.getMeterAnalysisStatus()).isEqualTo(MeterAnalysisStatus.MANUAL);
    }

    @Test
    @DisplayName("방장을 뺀 동승자마다 송금 전 상태의 청구 행이 생긴다")
    void chargesRidersExceptHost() {
        Settlement settlement = persistSettlement();

        settlementService.requestManually(host.getId(), settlement.getId(), 18000);

        assertThat(chargesOf(settlement))
                .extracting(charge -> charge.getUser().getId())
                .containsExactlyInAnyOrder(rider1.getId(), rider2.getId());
        assertThat(chargesOf(settlement)).allSatisfy(charge -> {
            assertThat(charge.getAmount()).isEqualTo(6000);
            assertThat(charge.getAnalysisStatus()).isEqualTo(TransferAnalysisStatus.PENDING);
            assertThat(charge.isTransferCompleted()).isFalse();
            assertThat(charge.getTransferImageAnalysisTrialCount()).isZero();
            assertThat(charge.getForged()).isNull();
        });
    }

    @Test
    @DisplayName("방장을 뺀 동승자마다 정산 요청 알림을 만든다")
    void notifiesRidersExceptHost() {
        Settlement settlement = persistSettlement();

        settlementService.requestManually(host.getId(), settlement.getId(), 18000);

        verify(notificationCommandService).createForChatRoom(eq(rider1), eq(NotificationType.SETTLEMENT_REQUESTED),
                eq(settlement.getId()), any(), eq("정산 요청이 도착했어요"));
        verify(notificationCommandService).createForChatRoom(eq(rider2), eq(NotificationType.SETTLEMENT_REQUESTED),
                eq(settlement.getId()), any(), eq("정산 요청이 도착했어요"));
        verify(notificationCommandService, never()).createForChatRoom(eq(host), any(), any(), any(), any());
    }

    @Test
    @DisplayName("나누어떨어지지 않으면 동승자는 내림한 금액을 내고 나머지는 방장이 진다")
    void hostAbsorbsRemainder() {
        Settlement settlement = persistSettlement();

        RequestedSettlement requested = settlementService.requestManually(host.getId(), settlement.getId(), 10000);

        assertThat(requested.amountPerPerson()).isEqualTo(3333);
        assertThat(chargesOf(settlement)).extracting(SettlementParticipant::getAmount).containsOnly(3333);
    }

    @Test
    @DisplayName("운행 전에 나간 사람에게는 청구하지 않는다")
    void skipsLeftParticipant() {
        User leaver = entityManager.persist(bankAccountHolder("나간사람"));
        persistParticipant(leaver, INCOMPLETE);
        Settlement settlement = persistSettlement();

        settlementService.requestManually(host.getId(), settlement.getId(), 18000);

        assertThat(chargesOf(settlement))
                .extracting(charge -> charge.getUser().getId())
                .doesNotContain(leaver.getId());
    }

    @Test
    @DisplayName("미터기 분석 중이어도 금액을 입력할 수 있다")
    void allowsWhileAnalyzing() {
        Settlement settlement = persistSettlement();
        ReflectionTestUtils.setField(settlement, "meterAnalysisStatus", MeterAnalysisStatus.ANALYZING);
        entityManager.flush();

        settlementService.requestManually(host.getId(), settlement.getId(), 18000);

        assertThat(reload(settlement).getMeterAnalysisStatus()).isEqualTo(MeterAnalysisStatus.MANUAL);
    }

    @Test
    @DisplayName("이미 금액이 정해진 정산이면 409 ALREADY_REQUESTED이고 청구 행이 더 생기지 않는다")
    void rejectsSecondRequest() {
        Settlement settlement = persistSettlement();
        settlementService.requestManually(host.getId(), settlement.getId(), 18000);

        assertErrorCode(() -> settlementService.requestManually(host.getId(), settlement.getId(), 30000),
                SettlementErrorCode.ALREADY_REQUESTED);
        assertThat(reload(settlement).getTotalAmount()).isEqualTo(18000);
        assertThat(chargesOf(settlement)).hasSize(2);
    }

    @Test
    @DisplayName("함께 탄 동승자가 입력하면 403 HOST_ONLY다")
    void rejectsRider() {
        Settlement settlement = persistSettlement();

        assertErrorCode(() -> settlementService.requestManually(rider1.getId(), settlement.getId(), 18000),
                TaxiPotErrorCode.HOST_ONLY);
        assertThat(reload(settlement).getStatus()).isEqualTo(SettlementStatus.PENDING);
        assertThat(chargesOf(settlement)).isEmpty();
        verify(notificationCommandService, never()).createForChatRoom(any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("함께 타지 않은 사람이 입력하면 404 SETTLEMENT_NOT_FOUND다")
    void hidesFromOutsider() {
        User outsider = entityManager.persist(bankAccountHolder("외부인"));
        User leaver = entityManager.persist(bankAccountHolder("나간사람"));
        persistParticipant(leaver, INCOMPLETE);
        Settlement settlement = persistSettlement();

        assertErrorCode(() -> settlementService.requestManually(outsider.getId(), settlement.getId(), 18000),
                SettlementErrorCode.SETTLEMENT_NOT_FOUND);
        assertErrorCode(() -> settlementService.requestManually(leaver.getId(), settlement.getId(), 18000),
                SettlementErrorCode.SETTLEMENT_NOT_FOUND);
    }

    @Test
    @DisplayName("없는 정산이면 404 SETTLEMENT_NOT_FOUND다")
    void missingSettlement() {
        assertErrorCode(() -> settlementService.requestManually(host.getId(), 999L, 18000),
                SettlementErrorCode.SETTLEMENT_NOT_FOUND);
    }

    @ParameterizedTest(name = "함께 탄 사람이 {0}")
    @EnumSource(value = OutcomeStatus.class, names = {"COMPLETED", "PENDING"})
    @DisplayName("함께 탄 사람이 완주 처리 전이든 후든 방장을 뺀 모두에게 청구한다")
    void chargesRidersRegardlessOfCompletion(OutcomeStatus riderOutcome) {
        riderParticipations.forEach(p -> ReflectionTestUtils.setField(p, "outcomeStatus", riderOutcome));
        entityManager.flush();
        Settlement settlement = persistSettlement();

        settlementService.requestManually(host.getId(), settlement.getId(), 18000);

        assertThat(chargesOf(settlement))
                .extracting(charge -> charge.getUser().getId())
                .containsExactlyInAnyOrder(rider1.getId(), rider2.getId());
    }

    private CompanionParticipant persistParticipant(User user, OutcomeStatus outcome) {
        return entityManager.persist(participant(pot, user, outcome));
    }

    private Settlement persistSettlement() {
        return entityManager.persistAndFlush(Settlement.open(pot));
    }

    private Settlement reload(Settlement settlement) {
        entityManager.flush();
        entityManager.clear();
        return entityManager.find(Settlement.class, settlement.getId());
    }

    private List<SettlementParticipant> chargesOf(Settlement settlement) {
        entityManager.flush();
        entityManager.clear();
        return entityManager.getEntityManager()
                .createQuery("SELECT p FROM SettlementParticipant p JOIN FETCH p.user WHERE p.settlement.id = :id",
                        SettlementParticipant.class)
                .setParameter("id", settlement.getId())
                .getResultList();
    }

    private static void assertErrorCode(ThrowingCallable call, ErrorCode expected) {
        assertThatThrownBy(call)
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(expected);
    }
}
