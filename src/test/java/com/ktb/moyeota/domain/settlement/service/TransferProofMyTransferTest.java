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
import com.ktb.moyeota.domain.settlement.entity.SettlementStatus;
import com.ktb.moyeota.domain.settlement.entity.TransferAnalysisStatus;
import com.ktb.moyeota.domain.settlement.error.SettlementErrorCode;
import com.ktb.moyeota.domain.settlement.model.MyTransfer;
import com.ktb.moyeota.domain.user.entity.User;
import com.ktb.moyeota.global.crypto.AccountNoCipher;
import com.ktb.moyeota.global.crypto.CryptoProperties;
import com.ktb.moyeota.global.exception.BusinessException;
import com.ktb.moyeota.global.exception.ErrorCode;
import com.ktb.moyeota.global.external.s3.S3PresignerTestSupport;
import com.ktb.moyeota.domain.notification.service.NotificationCommandService;
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

@DataJpaTest
@Import({TransferProofService.class, SettlementService.class, AccountNoCipher.class, ImageReadUrlIssuer.class,
        S3PresignerTestSupport.class})
@EnableConfigurationProperties(CryptoProperties.class)
class TransferProofMyTransferTest {

    @MockitoBean
    private NotificationCommandService notificationCommandService;

    @Autowired
    private TransferProofService transferProofService;

    @Autowired
    private SettlementService settlementService;

    @Autowired
    private AccountNoCipher accountNoCipher;

    @Autowired
    private TestEntityManager entityManager;

    private User host;
    private User rider;
    private Companion pot;
    private Settlement settlement;

    @BeforeEach
    void setUp() {
        host = entityManager.persist(bankAccountHolder("방장", accountNoCipher.encrypt("11012345678")));
        rider = entityManager.persist(bankAccountHolder("동승자"));
        User otherRider = entityManager.persist(bankAccountHolder("동승자2"));
        pot = entityManager.persist(taxiPot(host, CompanionStatus.COMPLETED, 3));
        entityManager.persist(ChatRoom.create(pot));
        entityManager.persist(participant(pot, host, COMPLETED));
        entityManager.persist(participant(pot, rider, COMPLETED));
        entityManager.persist(participant(pot, otherRider, COMPLETED));
        settlement = entityManager.persistAndFlush(Settlement.open(pot));
    }

    @Test
    @DisplayName("금액이 정해지기 전에는 정산 인원과 대기 상태만 돌려준다")
    void beforeAmountDecided() {
        MyTransfer transfer = transferProofService.findMine(rider.getId(), settlement.getId());

        assertThat(transfer.participantCount()).isEqualTo(3);
        assertThat(transfer.status()).isEqualTo(SettlementStatus.PENDING);
        assertThat(transfer.charge()).isEmpty();
    }

    @Test
    @DisplayName("금액이 정해지면 내가 보낼 금액 · 송금 전 상태 · 방장 이름과 복호화한 계좌를 돌려준다")
    void afterAmountDecided() {
        settlementService.requestManually(host.getId(), settlement.getId(), 18000);
        flushAndClear();

        MyTransfer transfer = transferProofService.findMine(rider.getId(), settlement.getId());

        assertThat(transfer.status()).isEqualTo(SettlementStatus.REQUESTED);
        assertThat(transfer.charge()).hasValueSatisfying(charge -> {
            assertThat(charge.amount()).isEqualTo(6000);
            assertThat(charge.analysisStatus()).isEqualTo(TransferAnalysisStatus.PENDING);
            assertThat(charge.transferredAt()).isNull();
            assertThat(charge.payee()).isEqualTo(new MyTransfer.Payee("홍길동", "kb", "11012345678"));
        });
    }

    @Test
    @DisplayName("방장이 운행 뒤 계좌를 바꿔도 정산을 만들 때의 계좌를 돌려준다")
    void keepsPayeeSnapshot() {
        settlementService.requestManually(host.getId(), settlement.getId(), 18000);
        entityManager.find(User.class, host.getId()).registerBankAccount("toss", accountNoCipher.encrypt("99999999999"));
        flushAndClear();

        MyTransfer transfer = transferProofService.findMine(rider.getId(), settlement.getId());

        assertThat(transfer.charge()).hasValueSatisfying(charge ->
                assertThat(charge.payee()).isEqualTo(new MyTransfer.Payee("홍길동", "kb", "11012345678")));
    }

    @Test
    @DisplayName("방장이 조회하면 403 PAYEE_HAS_NO_TRANSFER다")
    void rejectsPayee() {
        assertErrorCode(() -> transferProofService.findMine(host.getId(), settlement.getId()),
                SettlementErrorCode.PAYEE_HAS_NO_TRANSFER);
    }

    @Test
    @DisplayName("함께 타지 않은 사람 · 운행 전에 나간 사람 · 없는 정산은 404 SETTLEMENT_NOT_FOUND다")
    void hiddenFromOthers() {
        User outsider = entityManager.persist(bankAccountHolder("외부인"));
        User leaver = entityManager.persist(bankAccountHolder("나간사람"));
        entityManager.persistAndFlush(participant(pot, leaver, INCOMPLETE));

        assertErrorCode(() -> transferProofService.findMine(outsider.getId(), settlement.getId()),
                SettlementErrorCode.SETTLEMENT_NOT_FOUND);
        assertErrorCode(() -> transferProofService.findMine(leaver.getId(), settlement.getId()),
                SettlementErrorCode.SETTLEMENT_NOT_FOUND);
        assertErrorCode(() -> transferProofService.findMine(rider.getId(), 999L),
                SettlementErrorCode.SETTLEMENT_NOT_FOUND);
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
