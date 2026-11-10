package com.ktb.moyeota.domain.settlement.service;

import static com.ktb.moyeota.domain.chat.entity.OutcomeStatus.COMPLETED;
import static com.ktb.moyeota.domain.chat.entity.OutcomeStatus.INCOMPLETE;
import static com.ktb.moyeota.fixture.CompanionFixture.taxiPot;
import static com.ktb.moyeota.fixture.ParticipantFixture.participant;
import static com.ktb.moyeota.fixture.UserFixture.bankAccountHolder;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ktb.moyeota.domain.chat.entity.ChatRoom;
import com.ktb.moyeota.domain.companion.entity.Companion;
import com.ktb.moyeota.domain.companion.entity.CompanionStatus;
import com.ktb.moyeota.domain.image.service.ImageReadUrlIssuer;
import com.ktb.moyeota.domain.settlement.entity.Settlement;
import com.ktb.moyeota.domain.settlement.entity.SettlementParticipant;
import com.ktb.moyeota.domain.settlement.entity.TransferAnalysisStatus;
import com.ktb.moyeota.domain.settlement.error.SettlementErrorCode;
import com.ktb.moyeota.domain.settlement.model.TransferProof;
import com.ktb.moyeota.domain.user.entity.User;
import com.ktb.moyeota.global.crypto.AccountNoCipher;
import com.ktb.moyeota.global.crypto.CryptoProperties;
import com.ktb.moyeota.global.exception.BusinessException;
import com.ktb.moyeota.global.exception.ErrorCode;
import com.ktb.moyeota.global.external.s3.S3PresignerTestSupport;
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
@Import({TransferProofService.class, SettlementService.class, AccountNoCipher.class, ImageReadUrlIssuer.class,
        S3PresignerTestSupport.class})
@EnableConfigurationProperties(CryptoProperties.class)
class TransferProofQueryTest {

    @MockitoBean
    private NotificationCommandService notificationCommandService;

    private static final String PROOF_KEY = "transfer/0b9c2f7e.jpg";
    private static final String OTHER_PROOF_KEY = "transfer/7d1e4a90.jpg";
    private static final LocalDateTime TRANSFERRED_AT = LocalDateTime.of(2026, 9, 7, 9, 1);

    @Autowired
    private TransferProofService transferProofService;

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
        rider = entityManager.persist(bankAccountHolder("동승자"));
        otherRider = entityManager.persist(bankAccountHolder("동승자2"));
        pot = entityManager.persist(taxiPot(host, CompanionStatus.COMPLETED, 3));
        entityManager.persist(ChatRoom.create(pot));
        entityManager.persist(participant(pot, host, COMPLETED));
        entityManager.persist(participant(pot, rider, COMPLETED));
        entityManager.persist(participant(pot, otherRider, COMPLETED));
        settlement = entityManager.persistAndFlush(Settlement.open(pot));
    }

    @Test
    @DisplayName("방장은 동승자의 인증 사진을 만료형 주소로 받고 분석 상태 · 송금 시각도 함께 받는다")
    void hostSeesRiderProof() {
        requestPayment();
        submitProof(rider, PROOF_KEY);

        TransferProof proof = transferProofService.find(host.getId(), settlement.getId(), rider.getId());

        assertThat(proof.analysisStatus()).isEqualTo(TransferAnalysisStatus.PASSED);
        assertThat(proof.imageUrl()).contains(PROOF_KEY).contains("X-Amz-Expires=1800");
        assertThat(proof.transferredAt()).isEqualTo(TRANSFERRED_AT);
    }

    @Test
    @DisplayName("방장은 동승자가 여럿이어도 각자의 인증 사진을 따로 볼 수 있다")
    void hostSeesEachRiderProof() {
        requestPayment();
        submitProof(rider, PROOF_KEY);
        submitProof(otherRider, OTHER_PROOF_KEY);

        String riderUrl = transferProofService.find(host.getId(), settlement.getId(), rider.getId()).imageUrl();
        String otherRiderUrl = transferProofService.find(host.getId(), settlement.getId(), otherRider.getId()).imageUrl();

        assertThat(riderUrl).contains(PROOF_KEY).doesNotContain(OTHER_PROOF_KEY);
        assertThat(otherRiderUrl).contains(OTHER_PROOF_KEY).doesNotContain(PROOF_KEY);
    }

    @Test
    @DisplayName("동승자는 자기 인증 사진을 볼 수 있다")
    void riderSeesOwnProof() {
        requestPayment();
        submitProof(rider, PROOF_KEY);

        assertThat(transferProofService.find(rider.getId(), settlement.getId(), rider.getId()).imageUrl())
                .contains(PROOF_KEY);
    }

    @Test
    @DisplayName("아직 사진을 내지 않았으면 송금 전 상태만 있고 주소 · 송금 시각은 비어 있다")
    void beforeSubmission() {
        requestPayment();

        TransferProof proof = transferProofService.find(host.getId(), settlement.getId(), rider.getId());

        assertThat(proof).isEqualTo(new TransferProof(TransferAnalysisStatus.PENDING, null, null));
    }

    @Test
    @DisplayName("다른 동승자의 사진을 보려 하면 403 TRANSFER_PROOF_FORBIDDEN이다")
    void rejectsOtherRider() {
        requestPayment();
        submitProof(rider, PROOF_KEY);

        assertErrorCode(() -> transferProofService.find(otherRider.getId(), settlement.getId(), rider.getId()),
                SettlementErrorCode.TRANSFER_PROOF_FORBIDDEN);
    }

    @Test
    @DisplayName("함께 타지 않은 사람 · 운행 전에 나간 사람 · 없는 정산은 404 SETTLEMENT_NOT_FOUND다")
    void hiddenFromOthers() {
        User outsider = entityManager.persist(bankAccountHolder("외부인"));
        User leaver = entityManager.persist(bankAccountHolder("나간사람"));
        entityManager.persist(participant(pot, leaver, INCOMPLETE));
        requestPayment();

        assertErrorCode(() -> transferProofService.find(outsider.getId(), settlement.getId(), rider.getId()),
                SettlementErrorCode.SETTLEMENT_NOT_FOUND);
        assertErrorCode(() -> transferProofService.find(leaver.getId(), settlement.getId(), leaver.getId()),
                SettlementErrorCode.SETTLEMENT_NOT_FOUND);
        assertErrorCode(() -> transferProofService.find(host.getId(), 999L, rider.getId()),
                SettlementErrorCode.SETTLEMENT_NOT_FOUND);
    }

    @Test
    @DisplayName("청구를 받은 적 없는 사람의 송금 내역은 404 TRANSFER_NOT_FOUND다")
    void noChargeForTarget() {
        User outsider = entityManager.persist(bankAccountHolder("외부인"));
        requestPayment();

        assertErrorCode(() -> transferProofService.find(host.getId(), settlement.getId(), host.getId()),
                SettlementErrorCode.TRANSFER_NOT_FOUND);
        assertErrorCode(() -> transferProofService.find(host.getId(), settlement.getId(), outsider.getId()),
                SettlementErrorCode.TRANSFER_NOT_FOUND);
    }

    @Test
    @DisplayName("금액이 정해지기 전에는 본인 송금 내역도 404 TRANSFER_NOT_FOUND다")
    void beforeAmountDecided() {
        assertErrorCode(() -> transferProofService.find(rider.getId(), settlement.getId(), rider.getId()),
                SettlementErrorCode.TRANSFER_NOT_FOUND);
    }

    private void requestPayment() {
        settlementService.requestManually(host.getId(), settlement.getId(), 18000);
        entityManager.flush();
        entityManager.clear();
    }

    private void submitProof(User payer, String key) {
        SettlementParticipant charge = entityManager.getEntityManager()
                .createQuery("SELECT p FROM SettlementParticipant p WHERE p.settlement.id = :sid AND p.user.id = :uid",
                        SettlementParticipant.class)
                .setParameter("sid", settlement.getId())
                .setParameter("uid", payer.getId())
                .getSingleResult();
        ReflectionTestUtils.setField(charge, "transferImageUrl", key);
        ReflectionTestUtils.setField(charge, "analysisStatus", TransferAnalysisStatus.PASSED);
        ReflectionTestUtils.setField(charge, "transferredAt", TRANSFERRED_AT);
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
