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
import com.ktb.moyeota.domain.settlement.entity.SettlementStatus;
import com.ktb.moyeota.domain.settlement.error.SettlementErrorCode;
import com.ktb.moyeota.domain.settlement.model.MeterAnalysis;
import com.ktb.moyeota.domain.taxipot.error.TaxiPotErrorCode;
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
import org.springframework.test.util.ReflectionTestUtils;

@DataJpaTest
@Import({MeterImageService.class, MeterImageSubmissionTest.Config.class})
class MeterImageSubmissionTest {

    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 9, 7, 9, 0, 3, 789_000_000);
    private static final String FILE_NAME = "2aac932d-f68b-4491-9891-fd47671fcf02.jpg";
    private static final String METER_KEY = "meter/" + FILE_NAME;

    @MockitoBean
    private ImagePromotionService imagePromotionService;

    @Autowired
    private MeterImageService meterImageService;

    @Autowired
    private TestEntityManager entityManager;

    private User host;
    private User rider;
    private Settlement settlement;
    private String tmpKey;

    @BeforeEach
    void setUp() {
        host = entityManager.persist(bankAccountHolder("방장"));
        rider = entityManager.persist(bankAccountHolder("동승자"));
        Companion pot = entityManager.persist(taxiPot(host, CompanionStatus.COMPLETED, 2));
        entityManager.persist(ChatRoom.create(pot));
        entityManager.persist(participant(pot, host, COMPLETED));
        entityManager.persist(participant(pot, rider, COMPLETED));
        settlement = entityManager.persistAndFlush(Settlement.open(pot));
        tmpKey = "tmp/meter/u-" + host.getId() + "/" + FILE_NAME;
        given(imagePromotionService.promote(UploadScope.user(host.getId()), ImagePurpose.METER, tmpKey))
                .willReturn(METER_KEY);
    }

    @Test
    @DisplayName("방장이 사진을 내면 확정 키를 저장하고 분석 중으로 바꾸며, 시작 시각은 초 단위로 남긴다")
    void submit() {
        MeterAnalysis analysis = meterImageService.submit(host.getId(), settlement.getId(), tmpKey);

        LocalDateTime startedAt = LocalDateTime.of(2026, 9, 7, 9, 0, 3);
        assertThat(analysis).isEqualTo(
                new MeterAnalysis(SettlementStatus.PENDING, MeterAnalysisStatus.ANALYZING, null, startedAt, 1));
        Settlement saved = reload();
        assertThat(saved.getMeterImageUrl()).isEqualTo(METER_KEY);
        assertThat(saved.getMeterAnalysisStatus()).isEqualTo(MeterAnalysisStatus.ANALYZING);
        assertThat(saved.getAnalysisTrialCount()).isEqualTo(1);
        assertThat(saved.getAnalysisStartedAt()).isEqualTo(startedAt);
    }

    @Test
    @DisplayName("인식에 실패하면 한 번 더 낼 수 있고, 새 사진으로 바뀌며 남은 횟수는 0이 된다")
    void resubmitAfterFailure() {
        givenMeter(MeterAnalysisStatus.FAILED, 1);

        MeterAnalysis analysis = meterImageService.submit(host.getId(), settlement.getId(), tmpKey);

        assertThat(analysis.meterAnalysisStatus()).isEqualTo(MeterAnalysisStatus.ANALYZING);
        assertThat(analysis.remainingMeterAttempts()).isZero();
        Settlement saved = reload();
        assertThat(saved.getMeterImageUrl()).isEqualTo(METER_KEY);
        assertThat(saved.getAnalysisTrialCount()).isEqualTo(2);
    }

    @Test
    @DisplayName("두 번 다 실패했으면 409 RETRY_LIMIT_EXCEEDED이고 사진을 복사하지 않는다")
    void retryLimitExceeded() {
        givenMeter(MeterAnalysisStatus.FAILED, 2);

        assertRejected(() -> meterImageService.submit(host.getId(), settlement.getId(), tmpKey),
                SettlementErrorCode.RETRY_LIMIT_EXCEEDED);
        verifyNoInteractions(imagePromotionService);
        assertThat(reload().getAnalysisTrialCount()).isEqualTo(2);
    }

    @Test
    @DisplayName("분석 중이면 409 METER_ANALYSIS_IN_PROGRESS이고 사진을 복사하지 않는다")
    void analyzing() {
        givenMeter(MeterAnalysisStatus.ANALYZING, 1);

        assertRejected(() -> meterImageService.submit(host.getId(), settlement.getId(), tmpKey),
                SettlementErrorCode.METER_ANALYSIS_IN_PROGRESS);
        verifyNoInteractions(imagePromotionService);
    }

    @Test
    @DisplayName("금액이 이미 정해졌으면 409 ALREADY_REQUESTED이고 사진을 복사하지 않는다")
    void alreadyRequested() {
        ReflectionTestUtils.setField(settlement, "status", SettlementStatus.REQUESTED);
        ReflectionTestUtils.setField(settlement, "meterAnalysisStatus", MeterAnalysisStatus.MANUAL);
        ReflectionTestUtils.setField(settlement, "totalAmount", 18000);
        flushAndClear();

        assertRejected(() -> meterImageService.submit(host.getId(), settlement.getId(), tmpKey),
                SettlementErrorCode.ALREADY_REQUESTED);
        verifyNoInteractions(imagePromotionService);
    }

    @Test
    @DisplayName("사진을 복사하는 사이 방장이 금액을 입력했으면 409 ALREADY_REQUESTED이고 사진을 저장하지 않는다")
    void amountEnteredWhileCopying() {
        given(imagePromotionService.promote(any(), any(), any())).willAnswer(invocation -> {
            entityManager.getEntityManager()
                    .createQuery("update Settlement s set s.status = :status, s.meterAnalysisStatus = :meter,"
                            + " s.totalAmount = 18000 where s.id = :id")
                    .setParameter("status", SettlementStatus.REQUESTED)
                    .setParameter("meter", MeterAnalysisStatus.MANUAL)
                    .setParameter("id", settlement.getId())
                    .executeUpdate();
            entityManager.clear();
            return METER_KEY;
        });

        assertRejected(() -> meterImageService.submit(host.getId(), settlement.getId(), tmpKey),
                SettlementErrorCode.ALREADY_REQUESTED);
        Settlement saved = reload();
        assertThat(saved.getMeterImageUrl()).isNull();
        assertThat(saved.getMeterAnalysisStatus()).isEqualTo(MeterAnalysisStatus.MANUAL);
        assertThat(saved.getAnalysisTrialCount()).isZero();
    }

    @Test
    @DisplayName("올린 사진이 없으면 IMAGE_NOT_EXISTS이고 정산은 그대로다")
    void imageNotUploaded() {
        given(imagePromotionService.promote(any(), any(), any()))
                .willThrow(new BusinessException(ImageErrorCode.IMAGE_NOT_EXISTS));

        assertRejected(() -> meterImageService.submit(host.getId(), settlement.getId(), tmpKey),
                ImageErrorCode.IMAGE_NOT_EXISTS);
        Settlement saved = reload();
        assertThat(saved.getMeterAnalysisStatus()).isEqualTo(MeterAnalysisStatus.PENDING);
        assertThat(saved.getAnalysisTrialCount()).isZero();
    }

    @Test
    @DisplayName("함께 탄 동승자가 미터기 사진을 제출하려고 하면 403 HOST_ONLY다")
    void riderIsNotHost() {
        assertRejected(() -> meterImageService.submit(rider.getId(), settlement.getId(), tmpKey),
                TaxiPotErrorCode.HOST_ONLY);
        verifyNoInteractions(imagePromotionService);
    }

    @Test
    @DisplayName("함께 타지 않은 사람 · 운행 전에 나간 사람 · 없는 정산은 404 SETTLEMENT_NOT_FOUND다")
    void hiddenFromOthers() {
        User outsider = entityManager.persist(bankAccountHolder("외부인"));
        User leaver = entityManager.persist(bankAccountHolder("나간사람"));
        entityManager.persistAndFlush(participant(settlement.getCompanion(), leaver, OutcomeStatus.INCOMPLETE));

        assertRejected(() -> meterImageService.submit(outsider.getId(), settlement.getId(), tmpKey),
                SettlementErrorCode.SETTLEMENT_NOT_FOUND);
        assertRejected(() -> meterImageService.submit(leaver.getId(), settlement.getId(), tmpKey),
                SettlementErrorCode.SETTLEMENT_NOT_FOUND);
        assertRejected(() -> meterImageService.submit(host.getId(), 999L, tmpKey),
                SettlementErrorCode.SETTLEMENT_NOT_FOUND);
        verifyNoInteractions(imagePromotionService);
    }

    private void givenMeter(MeterAnalysisStatus status, int trialCount) {
        ReflectionTestUtils.setField(settlement, "meterAnalysisStatus", status);
        ReflectionTestUtils.setField(settlement, "analysisTrialCount", trialCount);
        flushAndClear();
    }

    private Settlement reload() {
        flushAndClear();
        return entityManager.find(Settlement.class, settlement.getId());
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
