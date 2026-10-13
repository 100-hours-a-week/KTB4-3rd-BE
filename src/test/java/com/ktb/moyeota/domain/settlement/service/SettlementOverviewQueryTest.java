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
import com.ktb.moyeota.domain.companion.entity.Companion;
import com.ktb.moyeota.domain.companion.entity.CompanionStatus;
import com.ktb.moyeota.domain.image.service.ImageUrlResolver;
import com.ktb.moyeota.domain.settlement.entity.MeterAnalysisStatus;
import com.ktb.moyeota.domain.settlement.entity.Settlement;
import com.ktb.moyeota.domain.settlement.entity.SettlementParticipant;
import com.ktb.moyeota.domain.settlement.entity.SettlementStatus;
import com.ktb.moyeota.domain.settlement.entity.TransferAnalysisStatus;
import com.ktb.moyeota.domain.settlement.error.SettlementErrorCode;
import com.ktb.moyeota.domain.settlement.model.SettlementOverview;
import com.ktb.moyeota.domain.taxipot.error.TaxiPotErrorCode;
import com.ktb.moyeota.domain.user.entity.User;
import com.ktb.moyeota.global.exception.BusinessException;
import com.ktb.moyeota.global.exception.ErrorCode;
import com.ktb.moyeota.global.external.s3.S3Properties;
import com.ktb.moyeota.domain.notification.service.NotificationCommandService;
import java.time.LocalDateTime;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.util.ReflectionTestUtils;

@DataJpaTest
@Import({SettlementOverviewService.class, SettlementService.class, ImageUrlResolver.class})
@EnableConfigurationProperties(S3Properties.class)
class SettlementOverviewQueryTest {

    @MockitoBean
    private NotificationCommandService notificationCommandService;

    private static final LocalDateTime TRANSFERRED_AT = LocalDateTime.of(2026, 9, 7, 9, 1);

    @Autowired
    private SettlementOverviewService settlementOverviewService;

    @Autowired
    private SettlementService settlementService;

    @Autowired
    private TestEntityManager entityManager;

    private User host;
    private User rider;
    private User otherRider;
    private Companion pot;
    private Settlement settlement;

    @BeforeEach
    void setUp() {
        host = entityManager.persist(bankAccountHolder("방장"));
        rider = bankAccountHolder("동승자");
        rider.changeProfileImage("profile/rider.png");
        entityManager.persist(rider);
        otherRider = entityManager.persist(bankAccountHolder("동승자2"));
        pot = entityManager.persist(taxiPot(host, CompanionStatus.COMPLETED, 3));
        entityManager.persist(ChatRoom.create(pot));
        entityManager.persist(participant(pot, host, PENDING));
        entityManager.persist(participant(pot, rider, PENDING));
        entityManager.persist(participant(pot, otherRider, PENDING));
        settlement = entityManager.persistAndFlush(Settlement.open(pot));
    }

    @Test
    @DisplayName("금액이 정해지기 전에는 정산 상태만 있고 송금 참여자는 비어 있다")
    void beforeAmountDecided() {
        SettlementOverview overview = settlementOverviewService.find(host.getId(), settlement.getId());

        assertThat(overview.id()).isEqualTo(settlement.getId());
        assertThat(overview.status()).isEqualTo(SettlementStatus.PENDING);
        assertThat(overview.meterAnalysisStatus()).isEqualTo(MeterAnalysisStatus.PENDING);
        assertThat(overview.totalAmount()).isNull();
        assertThat(overview.amountPerPerson()).isNull();
        assertThat(overview.participantCount()).isEqualTo(3);
        assertThat(overview.payers()).isEmpty();
    }

    @Test
    @DisplayName("금액이 정해지면 총액 · 인당 금액과 방장을 뺀 동승자별 송금 상태를 돌려준다")
    void afterAmountDecided() {
        requestPayment();

        SettlementOverview overview = settlementOverviewService.find(host.getId(), settlement.getId());

        assertThat(overview.status()).isEqualTo(SettlementStatus.REQUESTED);
        assertThat(overview.totalAmount()).isEqualTo(10000);
        assertThat(overview.amountPerPerson()).isEqualTo(3333);
        assertThat(overview.payers())
                .extracting(SettlementOverview.Payer::userId)
                .containsExactly(rider.getId(), otherRider.getId());
        assertThat(overview.payers()).allSatisfy(payer -> {
            assertThat(payer.amount()).isEqualTo(3333);
            assertThat(payer.analysisStatus()).isEqualTo(TransferAnalysisStatus.PENDING);
            assertThat(payer.transferredAt()).isNull();
            assertThat(payer.hasTransferProof()).isFalse();
        });
    }

    @Test
    @DisplayName("동승자의 이름 · 프로필 주소를 함께 준다")
    void includesPayerProfile() {
        requestPayment();

        SettlementOverview.Payer payer = settlementOverviewService.find(host.getId(), settlement.getId())
                .payers().getFirst();

        assertThat(payer.name()).isEqualTo("홍길동");
        assertThat(payer.profileImageUrl()).endsWith("/profile/rider.png");
    }

    @Test
    @DisplayName("송금 인증을 낸 동승자는 분석 상태 · 송금 시각 · 사진 여부가 바뀌고 다른 동승자는 그대로다")
    void reflectsEachTransfer() {
        requestPayment();
        submitProof(rider);

        SettlementOverview overview = settlementOverviewService.find(host.getId(), settlement.getId());

        assertThat(overview.payers().get(0)).satisfies(payer -> {
            assertThat(payer.analysisStatus()).isEqualTo(TransferAnalysisStatus.PASSED);
            assertThat(payer.transferredAt()).isEqualTo(TRANSFERRED_AT);
            assertThat(payer.hasTransferProof()).isTrue();
        });
        assertThat(overview.payers().get(1)).satisfies(payer -> {
            assertThat(payer.analysisStatus()).isEqualTo(TransferAnalysisStatus.PENDING);
            assertThat(payer.hasTransferProof()).isFalse();
        });
    }

    @Test
    @DisplayName("정산을 완료하면 완료 상태와 완료 시각을 돌려준다")
    void afterCompletion() {
        requestPayment();
        settlementService.complete(host.getId(), settlement.getId());
        flushAndClear();

        SettlementOverview overview = settlementOverviewService.find(host.getId(), settlement.getId());

        assertThat(overview.status()).isEqualTo(SettlementStatus.COMPLETED);
        assertThat(overview.completedAt()).isNotNull();
        assertThat(overview.payers()).hasSize(2);
    }

    @Test
    @DisplayName("함께 탄 동승자가 조회하면 403 HOST_ONLY다")
    void rejectsRider() {
        assertErrorCode(() -> settlementOverviewService.find(rider.getId(), settlement.getId()),
                TaxiPotErrorCode.HOST_ONLY);
    }

    @Test
    @DisplayName("함께 타지 않은 사람 · 운행 전에 나간 사람 · 없는 정산은 404 SETTLEMENT_NOT_FOUND다")
    void hiddenFromOthers() {
        User outsider = entityManager.persist(bankAccountHolder("외부인"));
        User leaver = entityManager.persist(bankAccountHolder("나간사람"));
        entityManager.persistAndFlush(participant(pot, leaver, INCOMPLETE));

        assertErrorCode(() -> settlementOverviewService.find(outsider.getId(), settlement.getId()),
                SettlementErrorCode.SETTLEMENT_NOT_FOUND);
        assertErrorCode(() -> settlementOverviewService.find(leaver.getId(), settlement.getId()),
                SettlementErrorCode.SETTLEMENT_NOT_FOUND);
        assertErrorCode(() -> settlementOverviewService.find(host.getId(), 999L),
                SettlementErrorCode.SETTLEMENT_NOT_FOUND);
    }

    private void requestPayment() {
        settlementService.requestManually(host.getId(), settlement.getId(), 10000);
        flushAndClear();
    }

    private void submitProof(User payer) {
        SettlementParticipant charge = entityManager.getEntityManager()
                .createQuery("SELECT p FROM SettlementParticipant p WHERE p.settlement.id = :sid AND p.user.id = :uid",
                        SettlementParticipant.class)
                .setParameter("sid", settlement.getId())
                .setParameter("uid", payer.getId())
                .getSingleResult();
        ReflectionTestUtils.setField(charge, "transferImageUrl", "transfer/rider.jpg");
        ReflectionTestUtils.setField(charge, "analysisStatus", TransferAnalysisStatus.PASSED);
        ReflectionTestUtils.setField(charge, "transferredAt", TRANSFERRED_AT);
        flushAndClear();
    }

    private void flushAndClear() {
        entityManager.flush();
        entityManager.clear();
    }

    private static void assertErrorCode(ThrowingCallable call, ErrorCode expected) {
        assertThatThrownBy(call)
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(expected);
    }
}
