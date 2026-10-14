package com.ktb.moyeota.domain.settlement.service;

import static com.ktb.moyeota.domain.chat.entity.OutcomeStatus.COMPLETED;
import static com.ktb.moyeota.fixture.CompanionFixture.taxiPot;
import static com.ktb.moyeota.fixture.ParticipantFixture.participant;
import static com.ktb.moyeota.fixture.UserFixture.bankAccountHolder;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verifyNoInteractions;

import com.ktb.moyeota.domain.chat.entity.ChatRoom;
import com.ktb.moyeota.domain.chat.entity.OutcomeStatus;
import com.ktb.moyeota.domain.companion.entity.Companion;
import com.ktb.moyeota.domain.companion.entity.CompanionStatus;
import com.ktb.moyeota.domain.image.error.ImageErrorCode;
import com.ktb.moyeota.domain.image.model.ImagePurpose;
import com.ktb.moyeota.domain.image.model.UploadScope;
import com.ktb.moyeota.domain.image.service.ImagePromotionService;
import com.ktb.moyeota.domain.settlement.entity.MeterAnalysisStatus;
import com.ktb.moyeota.domain.settlement.entity.Settlement;
import com.ktb.moyeota.domain.settlement.entity.SettlementParticipant;
import com.ktb.moyeota.domain.settlement.entity.SettlementStatus;
import com.ktb.moyeota.domain.settlement.entity.TransferAnalysisStatus;
import com.ktb.moyeota.domain.settlement.error.SettlementErrorCode;
import com.ktb.moyeota.domain.settlement.event.TransferAnalysisRequestedEvent;
import com.ktb.moyeota.domain.settlement.model.TransferSubmission;
import com.ktb.moyeota.domain.user.entity.User;
import com.ktb.moyeota.global.exception.BusinessException;
import com.ktb.moyeota.global.exception.ErrorCode;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;
import org.springframework.test.util.ReflectionTestUtils;

@DataJpaTest
@RecordApplicationEvents
@Import({TransferProofSubmissionService.class, TransferProofSubmissionTest.Config.class})
class TransferProofSubmissionTest {

    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 9, 7, 9, 10, 3);
    private static final String FILE_NAME = "2aac932d-f68b-4491-9891-fd47671fcf02.jpg";
    private static final String TRANSFER_KEY = "transfer/" + FILE_NAME;

    @MockitoBean
    private ImagePromotionService imagePromotionService;

    @Autowired
    private TransferProofSubmissionService transferProofSubmissionService;

    @Autowired
    private TestEntityManager entityManager;

    @Autowired
    private ApplicationEvents events;

    private User host;
    private User rider;
    private Settlement settlement;
    private SettlementParticipant charge;
    private String tmpKey;

    @BeforeEach
    void setUp() {
        host = entityManager.persist(bankAccountHolder("방장"));
        rider = entityManager.persist(bankAccountHolder("동승자"));
        User otherRider = entityManager.persist(bankAccountHolder("동승자2"));
        Companion pot = entityManager.persist(taxiPot(host, CompanionStatus.COMPLETED, 3));
        entityManager.persist(ChatRoom.create(pot));
        entityManager.persist(participant(pot, host, COMPLETED));
        entityManager.persist(participant(pot, rider, COMPLETED));
        entityManager.persist(participant(pot, otherRider, COMPLETED));
        settlement = Settlement.open(pot);
        ReflectionTestUtils.setField(settlement, "status", SettlementStatus.REQUESTED);
        ReflectionTestUtils.setField(settlement, "meterAnalysisStatus", MeterAnalysisStatus.MANUAL);
        ReflectionTestUtils.setField(settlement, "totalAmount", 18000);
        entityManager.persist(settlement);
        charge = entityManager.persist(SettlementParticipant.charge(settlement, rider, 6000));
        entityManager.persistAndFlush(SettlementParticipant.charge(settlement, otherRider, 6000));
        tmpKey = "tmp/transfer/u-" + rider.getId() + "/" + FILE_NAME;
        given(imagePromotionService.promote(UploadScope.user(rider.getId()), ImagePurpose.TRANSFER, tmpKey))
                .willReturn(TRANSFER_KEY);
    }

    @Test
    @DisplayName("동승자가 사진을 내면 확정 키를 저장하고 분석 중으로 바꾸며, 남은 횟수를 돌려주고 분석을 요청한다")
    void submit() {
        TransferSubmission submission = transferProofSubmissionService.submit(rider.getId(), settlement.getId(), tmpKey);

        assertThat(submission).isEqualTo(new TransferSubmission(SettlementStatus.REQUESTED, 2));
        SettlementParticipant saved = reloadCharge();
        assertThat(saved.getTransferImageUrl()).isEqualTo(TRANSFER_KEY);
        assertThat(saved.getAnalysisStatus()).isEqualTo(TransferAnalysisStatus.ANALYZING);
        assertThat(saved.getTransferImageAnalysisTrialCount()).isEqualTo(1);
        assertThat(saved.isTransferCompleted()).isFalse();
        assertThat(events.stream(TransferAnalysisRequestedEvent.class)).containsExactly(
                new TransferAnalysisRequestedEvent(settlement.getId(), rider.getId(), TRANSFER_KEY, 1, NOW));
    }

    @Test
    @DisplayName("AI 분석에 실패했으면 다시 낼 수 있고, 새 사진으로 바뀌며 남은 횟수가 줄어든다")
    void resubmitAfterFailure() {
        givenCharge(TransferAnalysisStatus.FAILED, 1, false);

        TransferSubmission submission = transferProofSubmissionService.submit(rider.getId(), settlement.getId(), tmpKey);

        assertThat(submission.remainingTransferAttempts()).isEqualTo(1);
        SettlementParticipant saved = reloadCharge();
        assertThat(saved.getAnalysisStatus()).isEqualTo(TransferAnalysisStatus.ANALYZING);
        assertThat(saved.getTransferImageAnalysisTrialCount()).isEqualTo(2);
        assertThat(events.stream(TransferAnalysisRequestedEvent.class))
                .singleElement()
                .extracting(TransferAnalysisRequestedEvent::attempt)
                .isEqualTo(2);
    }

    @Test
    @DisplayName("금액이 달랐으면 다시 낼 수 있고, 세 번째 제출이면 남은 횟수는 0이다")
    void resubmitAfterAmountMismatch() {
        givenCharge(TransferAnalysisStatus.PASSED, 2, false);

        TransferSubmission submission = transferProofSubmissionService.submit(rider.getId(), settlement.getId(), tmpKey);

        assertThat(submission.remainingTransferAttempts()).isZero();
        assertThat(reloadCharge().getTransferImageAnalysisTrialCount()).isEqualTo(3);
    }

    @Test
    @DisplayName("세 번 모두 냈으면 409 TRANSFER_RETRY_LIMIT_EXCEEDED이고 사진을 복사하지 않는다")
    void retryLimitExceeded() {
        givenCharge(TransferAnalysisStatus.FAILED, 3, false);

        assertRejected(() -> transferProofSubmissionService.submit(rider.getId(), settlement.getId(), tmpKey),
                SettlementErrorCode.TRANSFER_RETRY_LIMIT_EXCEEDED);
        verifyNoInteractions(imagePromotionService);
        assertThat(reloadCharge().getTransferImageAnalysisTrialCount()).isEqualTo(3);
    }

    @Test
    @DisplayName("분석 중이면 409 TRANSFER_ANALYSIS_IN_PROGRESS이고 사진을 복사하지 않는다")
    void analyzing() {
        givenCharge(TransferAnalysisStatus.ANALYZING, 1, false);

        assertRejected(() -> transferProofSubmissionService.submit(rider.getId(), settlement.getId(), tmpKey),
                SettlementErrorCode.TRANSFER_ANALYSIS_IN_PROGRESS);
        verifyNoInteractions(imagePromotionService);
    }

    @Test
    @DisplayName("이미 송금이 확인됐으면 409 ALREADY_TRANSFERRED이고 사진을 복사하지 않는다")
    void alreadyTransferred() {
        givenCharge(TransferAnalysisStatus.PASSED, 1, true);

        assertRejected(() -> transferProofSubmissionService.submit(rider.getId(), settlement.getId(), tmpKey),
                SettlementErrorCode.ALREADY_TRANSFERRED);
        verifyNoInteractions(imagePromotionService);
    }

    @Test
    @DisplayName("정산이 완료됐으면 409 TRANSFER_NOT_SUBMITTABLE이고 사진을 복사하지 않는다")
    void settlementCompleted() {
        ReflectionTestUtils.setField(settlement, "status", SettlementStatus.COMPLETED);
        flushAndClear();

        assertRejected(() -> transferProofSubmissionService.submit(rider.getId(), settlement.getId(), tmpKey),
                SettlementErrorCode.TRANSFER_NOT_SUBMITTABLE);
        verifyNoInteractions(imagePromotionService);
    }

    @Test
    @DisplayName("금액이 정해지기 전이면 409 TRANSFER_NOT_SUBMITTABLE이고 사진을 복사하지 않는다")
    void beforeAmountDecided() {
        Companion pot = entityManager.persist(taxiPot(host, CompanionStatus.COMPLETED, 2));
        entityManager.persist(ChatRoom.create(pot));
        entityManager.persist(participant(pot, host, COMPLETED));
        entityManager.persist(participant(pot, rider, COMPLETED));
        Settlement pending = entityManager.persistAndFlush(Settlement.open(pot));

        assertRejected(() -> transferProofSubmissionService.submit(rider.getId(), pending.getId(), tmpKey),
                SettlementErrorCode.TRANSFER_NOT_SUBMITTABLE);
        verifyNoInteractions(imagePromotionService);
    }

    @Test
    @DisplayName("사진을 복사하는 사이 방장이 정산을 완료했으면 409 TRANSFER_NOT_SUBMITTABLE이고 사진을 저장하지 않는다")
    void completedWhileCopying() {
        given(imagePromotionService.promote(any(), any(), any())).willAnswer(invocation -> {
            entityManager.getEntityManager()
                    .createQuery("update Settlement s set s.status = :status where s.id = :id")
                    .setParameter("status", SettlementStatus.COMPLETED)
                    .setParameter("id", settlement.getId())
                    .executeUpdate();
            entityManager.clear();
            return TRANSFER_KEY;
        });

        assertRejected(() -> transferProofSubmissionService.submit(rider.getId(), settlement.getId(), tmpKey),
                SettlementErrorCode.TRANSFER_NOT_SUBMITTABLE);
        SettlementParticipant saved = reloadCharge();
        assertThat(saved.getTransferImageUrl()).isNull();
        assertThat(saved.getAnalysisStatus()).isEqualTo(TransferAnalysisStatus.PENDING);
        assertThat(saved.getTransferImageAnalysisTrialCount()).isZero();
        assertThat(events.stream(TransferAnalysisRequestedEvent.class)).isEmpty();
    }

    @Test
    @DisplayName("올린 사진이 없으면 IMAGE_NOT_EXISTS이고 송금 상태는 그대로다")
    void imageNotUploaded() {
        given(imagePromotionService.promote(any(), any(), any()))
                .willThrow(new BusinessException(ImageErrorCode.IMAGE_NOT_EXISTS));

        assertRejected(() -> transferProofSubmissionService.submit(rider.getId(), settlement.getId(), tmpKey),
                ImageErrorCode.IMAGE_NOT_EXISTS);
        SettlementParticipant saved = reloadCharge();
        assertThat(saved.getAnalysisStatus()).isEqualTo(TransferAnalysisStatus.PENDING);
        assertThat(saved.getTransferImageAnalysisTrialCount()).isZero();
    }

    @Test
    @DisplayName("방장이 내려고 하면 403 PAYEE_HAS_NO_TRANSFER다")
    void payee() {
        assertRejected(() -> transferProofSubmissionService.submit(host.getId(), settlement.getId(), tmpKey),
                SettlementErrorCode.PAYEE_HAS_NO_TRANSFER);
        verifyNoInteractions(imagePromotionService);
    }

    @Test
    @DisplayName("함께 타지 않은 사람 · 운행 전에 나간 사람 · 없는 정산은 404 SETTLEMENT_NOT_FOUND다")
    void hiddenFromOthers() {
        User outsider = entityManager.persist(bankAccountHolder("외부인"));
        User leaver = entityManager.persist(bankAccountHolder("나간사람"));
        entityManager.persistAndFlush(participant(settlement.getCompanion(), leaver, OutcomeStatus.INCOMPLETE));

        assertRejected(() -> transferProofSubmissionService.submit(outsider.getId(), settlement.getId(), tmpKey),
                SettlementErrorCode.SETTLEMENT_NOT_FOUND);
        assertRejected(() -> transferProofSubmissionService.submit(leaver.getId(), settlement.getId(), tmpKey),
                SettlementErrorCode.SETTLEMENT_NOT_FOUND);
        assertRejected(() -> transferProofSubmissionService.submit(rider.getId(), 999L, tmpKey),
                SettlementErrorCode.SETTLEMENT_NOT_FOUND);
        verifyNoInteractions(imagePromotionService);
    }

    private void givenCharge(TransferAnalysisStatus status, int trialCount, boolean transferCompleted) {
        SettlementParticipant managed = entityManager.find(SettlementParticipant.class, charge.getId());
        ReflectionTestUtils.setField(managed, "analysisStatus", status);
        ReflectionTestUtils.setField(managed, "transferImageAnalysisTrialCount", trialCount);
        ReflectionTestUtils.setField(managed, "transferCompleted", transferCompleted);
        flushAndClear();
    }

    private SettlementParticipant reloadCharge() {
        flushAndClear();
        return entityManager.find(SettlementParticipant.class, charge.getId());
    }

    private void flushAndClear() {
        entityManager.flush();
        entityManager.clear();
    }

    private static void assertRejected(ThrowingCallable call, ErrorCode expected) {
        assertThatThrownBy(call)
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(expected);
    }

    @TestConfiguration
    static class Config {

        @Bean
        Clock clock() {
            return Clock.fixed(NOW.atZone(SEOUL).toInstant(), SEOUL);
        }
    }
}
