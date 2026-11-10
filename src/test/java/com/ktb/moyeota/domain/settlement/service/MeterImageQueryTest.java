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
import com.ktb.moyeota.domain.image.service.ImagePromotionService;
import com.ktb.moyeota.domain.image.service.ImageReadUrlIssuer;
import com.ktb.moyeota.domain.settlement.entity.MeterAnalysisStatus;
import com.ktb.moyeota.domain.settlement.entity.Settlement;
import com.ktb.moyeota.domain.settlement.entity.SettlementStatus;
import com.ktb.moyeota.domain.settlement.error.SettlementErrorCode;
import com.ktb.moyeota.domain.settlement.model.MeterImage;
import com.ktb.moyeota.domain.user.entity.User;
import com.ktb.moyeota.global.config.ClockConfig;
import com.ktb.moyeota.global.exception.BusinessException;
import com.ktb.moyeota.global.external.s3.S3PresignerTestSupport;
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
@Import({MeterImageService.class, ImageReadUrlIssuer.class, S3PresignerTestSupport.class, ClockConfig.class})
class MeterImageQueryTest {

    private static final String METER_KEY = "meter/2aac932d-f68b-4491-9891-fd47671fcf02.jpg";

    @MockitoBean
    private ImagePromotionService imagePromotionService;

    @Autowired
    private MeterImageService meterImageService;

    @Autowired
    private TestEntityManager entityManager;

    private User host;
    private User rider;
    private Companion pot;
    private Settlement settlement;

    @BeforeEach
    void setUp() {
        host = entityManager.persist(bankAccountHolder("방장"));
        rider = entityManager.persist(bankAccountHolder("동승자"));
        pot = entityManager.persist(taxiPot(host, CompanionStatus.COMPLETED, 2));
        entityManager.persist(ChatRoom.create(pot));
        entityManager.persist(participant(pot, host, COMPLETED));
        entityManager.persist(participant(pot, rider, COMPLETED));
        settlement = entityManager.persistAndFlush(Settlement.open(pot));
    }

    @Test
    @DisplayName("방장은 미터기 사진을 30분짜리 만료형 주소로 받고 분석 상태도 함께 받는다")
    void hostSeesMeterImage() {
        givenMeterImage(MeterAnalysisStatus.ANALYZING);

        MeterImage image = meterImageService.find(host.getId(), settlement.getId());

        assertThat(image.meterAnalysisStatus()).isEqualTo(MeterAnalysisStatus.ANALYZING);
        assertThat(image.imageUrl()).contains(METER_KEY).contains("X-Amz-Expires=1800");
    }

    @Test
    @DisplayName("함께 탄 동승자도 미터기 사진을 볼 수 있다")
    void riderSeesMeterImage() {
        givenMeterImage(MeterAnalysisStatus.PASSED);

        assertThat(meterImageService.find(rider.getId(), settlement.getId()).imageUrl()).contains(METER_KEY);
    }

    @Test
    @DisplayName("사진을 내기 전이면 분석 상태만 있고 주소는 비어 있다")
    void beforeSubmission() {
        MeterImage image = meterImageService.find(rider.getId(), settlement.getId());

        assertThat(image).isEqualTo(new MeterImage(MeterAnalysisStatus.PENDING, null));
    }

    @Test
    @DisplayName("사진 없이 금액을 직접 입력했으면 수동 입력 상태만 있고 주소는 비어 있다")
    void manualWithoutImage() {
        ReflectionTestUtils.setField(settlement, "status", SettlementStatus.REQUESTED);
        ReflectionTestUtils.setField(settlement, "meterAnalysisStatus", MeterAnalysisStatus.MANUAL);
        ReflectionTestUtils.setField(settlement, "totalAmount", 18000);
        flushAndClear();

        MeterImage image = meterImageService.find(rider.getId(), settlement.getId());

        assertThat(image).isEqualTo(new MeterImage(MeterAnalysisStatus.MANUAL, null));
    }

    @Test
    @DisplayName("함께 타지 않은 사람 · 운행 전에 나간 사람 · 없는 정산은 404 SETTLEMENT_NOT_FOUND다")
    void hiddenFromOthers() {
        User outsider = entityManager.persist(bankAccountHolder("외부인"));
        User leaver = entityManager.persist(bankAccountHolder("나간사람"));
        entityManager.persist(participant(pot, leaver, INCOMPLETE));
        givenMeterImage(MeterAnalysisStatus.ANALYZING);

        assertNotFound(() -> meterImageService.find(outsider.getId(), settlement.getId()));
        assertNotFound(() -> meterImageService.find(leaver.getId(), settlement.getId()));
        assertNotFound(() -> meterImageService.find(host.getId(), 999L));
    }

    private void givenMeterImage(MeterAnalysisStatus status) {
        ReflectionTestUtils.setField(settlement, "meterImageUrl", METER_KEY);
        ReflectionTestUtils.setField(settlement, "meterAnalysisStatus", status);
        ReflectionTestUtils.setField(settlement, "analysisTrialCount", 1);
        flushAndClear();
    }

    private void flushAndClear() {
        entityManager.flush();
        entityManager.clear();
    }

    private static void assertNotFound(ThrowingCallable call) {
        assertThatThrownBy(call)
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(SettlementErrorCode.SETTLEMENT_NOT_FOUND);
    }
}
