package com.ktb.moyeota.domain.settlement.service;

import static com.ktb.moyeota.domain.chat.entity.OutcomeStatus.COMPLETED;
import static com.ktb.moyeota.fixture.CompanionFixture.taxiPot;
import static com.ktb.moyeota.fixture.ParticipantFixture.participant;
import static com.ktb.moyeota.fixture.UserFixture.bankAccountHolder;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ktb.moyeota.domain.chat.entity.ChatRoom;
import com.ktb.moyeota.domain.chat.entity.OutcomeStatus;
import com.ktb.moyeota.domain.companion.entity.Companion;
import com.ktb.moyeota.domain.companion.entity.CompanionStatus;
import com.ktb.moyeota.domain.settlement.entity.MeterAnalysisStatus;
import com.ktb.moyeota.domain.settlement.entity.Settlement;
import com.ktb.moyeota.domain.settlement.entity.SettlementStatus;
import com.ktb.moyeota.domain.settlement.error.SettlementErrorCode;
import com.ktb.moyeota.domain.settlement.model.MeterAnalysis;
import com.ktb.moyeota.domain.user.entity.User;
import com.ktb.moyeota.global.exception.BusinessException;
import com.ktb.moyeota.domain.notification.service.NotificationCommandService;
import java.time.LocalDateTime;
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
class SettlementMeterAnalysisQueryTest {

    @MockitoBean
    private NotificationCommandService notificationCommandService;

    @Autowired
    private SettlementService settlementService;

    @Autowired
    private TestEntityManager entityManager;

    private User host;
    private User rider;
    private Settlement settlement;

    @BeforeEach
    void setUp() {
        host = entityManager.persist(bankAccountHolder("방장"));
        rider = entityManager.persist(bankAccountHolder("동승자"));
        Companion pot = entityManager.persist(taxiPot(host, CompanionStatus.COMPLETED, 2));
        entityManager.persist(ChatRoom.create(pot));
        entityManager.persist(participant(pot, host, COMPLETED));
        entityManager.persist(participant(pot, rider, COMPLETED));
        settlement = entityManager.persistAndFlush(Settlement.open(pot));
    }

    @Test
    @DisplayName("운행 종료 직후에는 사진 제출 전 상태와 빈 총액 · 시작 시각을 돌려준다")
    void beforeSubmission() {
        MeterAnalysis analysis = settlementService.findMeterAnalysis(host.getId(), settlement.getId());

        assertThat(analysis).isEqualTo(
                new MeterAnalysis(SettlementStatus.PENDING, MeterAnalysisStatus.PENDING, null, null));
    }

    @Test
    @DisplayName("분석 중이면 분석 상태와 시작 시각을 돌려준다")
    void whileAnalyzing() {
        LocalDateTime startedAt = LocalDateTime.of(2026, 9, 7, 9, 0, 3);
        ReflectionTestUtils.setField(settlement, "meterAnalysisStatus", MeterAnalysisStatus.ANALYZING);
        ReflectionTestUtils.setField(settlement, "analysisStartedAt", startedAt);
        entityManager.flush();
        entityManager.clear();

        MeterAnalysis analysis = settlementService.findMeterAnalysis(host.getId(), settlement.getId());

        assertThat(analysis.meterAnalysisStatus()).isEqualTo(MeterAnalysisStatus.ANALYZING);
        assertThat(analysis.analysisStartedAt()).isEqualTo(startedAt);
    }

    @Test
    @DisplayName("금액을 입력한 뒤에는 청구 단계 · 수동 입력 · 총액을 돌려준다")
    void afterManualInput() {
        settlementService.requestManually(host.getId(), settlement.getId(), 18000);
        entityManager.flush();
        entityManager.clear();

        MeterAnalysis analysis = settlementService.findMeterAnalysis(rider.getId(), settlement.getId());

        assertThat(analysis).isEqualTo(
                new MeterAnalysis(SettlementStatus.REQUESTED, MeterAnalysisStatus.MANUAL, 18000, null));
    }

    @Test
    @DisplayName("함께 탄 동승자도 조회할 수 있다")
    void riderCanRead() {
        assertThat(settlementService.findMeterAnalysis(rider.getId(), settlement.getId()).status())
                .isEqualTo(SettlementStatus.PENDING);
    }

    @Test
    @DisplayName("함께 타지 않은 사람 · 운행 전에 나간 사람 · 없는 정산은 404 SETTLEMENT_NOT_FOUND다")
    void hiddenFromOthers() {
        User outsider = entityManager.persist(bankAccountHolder("외부인"));
        User leaver = entityManager.persist(bankAccountHolder("나간사람"));
        entityManager.persistAndFlush(participant(settlement.getCompanion(), leaver, OutcomeStatus.INCOMPLETE));

        assertNotFound(() -> settlementService.findMeterAnalysis(outsider.getId(), settlement.getId()));
        assertNotFound(() -> settlementService.findMeterAnalysis(leaver.getId(), settlement.getId()));
        assertNotFound(() -> settlementService.findMeterAnalysis(host.getId(), 999L));
    }

    private static void assertNotFound(ThrowingCallable call) {
        assertThatThrownBy(call)
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(SettlementErrorCode.SETTLEMENT_NOT_FOUND);
    }
}
