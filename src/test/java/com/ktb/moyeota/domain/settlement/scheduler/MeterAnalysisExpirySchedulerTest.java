package com.ktb.moyeota.domain.settlement.scheduler;

import static com.ktb.moyeota.domain.chat.entity.OutcomeStatus.COMPLETED;
import static com.ktb.moyeota.fixture.CompanionFixture.taxiPot;
import static com.ktb.moyeota.fixture.ParticipantFixture.participant;
import static com.ktb.moyeota.fixture.UserFixture.bankAccountHolder;
import static org.assertj.core.api.Assertions.assertThat;

import com.ktb.moyeota.domain.chat.entity.ChatRoom;
import com.ktb.moyeota.domain.companion.entity.Companion;
import com.ktb.moyeota.domain.companion.entity.CompanionStatus;
import com.ktb.moyeota.domain.image.service.ImageReadUrlIssuer;
import com.ktb.moyeota.domain.notification.service.NotificationCommandService;
import com.ktb.moyeota.domain.settlement.config.MeterAnalysisProperties;
import com.ktb.moyeota.domain.settlement.dto.MeterAnalysisFinishedMessage;
import com.ktb.moyeota.domain.settlement.entity.MeterAnalysisStatus;
import com.ktb.moyeota.domain.settlement.entity.Settlement;
import com.ktb.moyeota.domain.settlement.entity.SettlementStatus;
import com.ktb.moyeota.domain.settlement.service.MeterAnalysisExpiryService;
import com.ktb.moyeota.domain.settlement.service.MeterAnalysisResultWriter;
import com.ktb.moyeota.domain.settlement.service.MeterAnalysisService;
import com.ktb.moyeota.domain.settlement.service.SettlementService;
import com.ktb.moyeota.domain.user.entity.User;
import com.ktb.moyeota.global.config.MutableClock;
import com.ktb.moyeota.global.config.TestClockConfig;
import com.ktb.moyeota.global.external.ai.AiAnalysisClient;
import com.ktb.moyeota.global.external.ai.AnalysisOutcome;
import com.ktb.moyeota.global.external.s3.S3PresignerTestSupport;
import com.ktb.moyeota.global.websocket.RecordingUserEventSender;
import com.ktb.moyeota.global.websocket.UserChannel;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.LocalDateTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.util.ReflectionTestUtils;

@DataJpaTest
@Import({MeterAnalysisExpiryScheduler.class, MeterAnalysisExpiryService.class, MeterAnalysisService.class,
        MeterAnalysisResultWriter.class, SettlementService.class, ImageReadUrlIssuer.class,
        S3PresignerTestSupport.class, TestClockConfig.class, MeterAnalysisExpirySchedulerTest.Config.class})
@EnableConfigurationProperties(MeterAnalysisProperties.class)
class MeterAnalysisExpirySchedulerTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 9, 14, 30, 0);

    @MockitoBean
    private AiAnalysisClient aiAnalysisClient;

    @MockitoBean
    private NotificationCommandService notificationCommandService;

    @Autowired
    private MutableClock clock;

    @Autowired
    private RecordingUserEventSender userEventSender;

    @Autowired
    private MeterAnalysisExpiryScheduler scheduler;

    @Autowired
    private MeterAnalysisService meterAnalysisService;

    @Autowired
    private TestEntityManager entityManager;

    private User host;
    private int riderCount;

    @BeforeEach
    void setUp() {
        clock.set(NOW);
        userEventSender.clear();
        host = entityManager.persist(bankAccountHolder("방장"));
    }

    @Test
    @DisplayName("분석을 시작한 지 5분이 지나도 결과가 없으면 시스템 실패로 바꾸고 기회를 돌려주며 방장에게 알린다")
    void expiresStaleAnalysis() {
        Settlement stale = analyzing(NOW.minusMinutes(5).minusSeconds(1));

        scheduler.sweep();

        Settlement saved = reload(stale);
        assertThat(saved.getMeterAnalysisStatus()).isEqualTo(MeterAnalysisStatus.FAILED);
        assertThat(saved.getStatus()).isEqualTo(SettlementStatus.PENDING);
        assertThat(saved.getAnalysisTrialCount()).isZero();
        assertThat(userEventSender.sent()).containsExactly(new RecordingUserEventSender.Sent(
                host.getId(), UserChannel.SETTLEMENT,
                new MeterAnalysisFinishedMessage("METER_ANALYSIS_FINISHED", stale.getId(), "FAILED", 2)));
    }

    @Test
    @DisplayName("시작한 지 5분이 안 된 분석은 그대로 둔다")
    void keepsRecentAnalysis() {
        Settlement recent = analyzing(NOW.minusMinutes(5));

        scheduler.sweep();

        assertThat(reload(recent).getMeterAnalysisStatus()).isEqualTo(MeterAnalysisStatus.ANALYZING);
        assertThat(userEventSender.sent()).isEmpty();
    }

    @Test
    @DisplayName("오래전에 시작했어도 이미 끝난 분석은 건드리지 않는다")
    void ignoresFinishedAnalysis() {
        Settlement passed = analyzing(NOW.minusHours(1));
        ReflectionTestUtils.setField(passed, "meterAnalysisStatus", MeterAnalysisStatus.PASSED);
        ReflectionTestUtils.setField(passed, "status", SettlementStatus.REQUESTED);
        entityManager.merge(passed);
        flushAndClear();

        scheduler.sweep();

        assertThat(reload(passed).getMeterAnalysisStatus()).isEqualTo(MeterAnalysisStatus.PASSED);
        assertThat(userEventSender.sent()).isEmpty();
    }

    @Test
    @DisplayName("만료 처리 뒤에 늦게 온 분석 결과는 버린다")
    void ignoresResultAfterExpiry() {
        LocalDateTime startedAt = NOW.minusMinutes(6);
        Settlement stale = analyzing(startedAt);
        scheduler.sweep();
        userEventSender.clear();

        meterAnalysisService.apply(stale.getId(), startedAt, new AnalysisOutcome.Read<>(18000));

        Settlement saved = reload(stale);
        assertThat(saved.getMeterAnalysisStatus()).isEqualTo(MeterAnalysisStatus.FAILED);
        assertThat(saved.getTotalAmount()).isNull();
        assertThat(userEventSender.sent()).isEmpty();
    }

    private Settlement analyzing(LocalDateTime startedAt) {
        User rider = entityManager.persist(bankAccountHolder("동승자" + (++riderCount)));
        Companion pot = entityManager.persist(taxiPot(host, CompanionStatus.COMPLETED, 2));
        entityManager.persist(ChatRoom.create(pot));
        entityManager.persist(participant(pot, host, COMPLETED));
        entityManager.persist(participant(pot, rider, COMPLETED));
        Settlement settlement = entityManager.persist(Settlement.open(pot));
        ReflectionTestUtils.setField(settlement, "meterImageUrl", "meter/a.jpg");
        ReflectionTestUtils.setField(settlement, "meterAnalysisStatus", MeterAnalysisStatus.ANALYZING);
        ReflectionTestUtils.setField(settlement, "analysisTrialCount", 1);
        ReflectionTestUtils.setField(settlement, "analysisStartedAt", startedAt);
        flushAndClear();
        return settlement;
    }

    private Settlement reload(Settlement settlement) {
        flushAndClear();
        return entityManager.find(Settlement.class, settlement.getId());
    }

    private void flushAndClear() {
        entityManager.flush();
        entityManager.clear();
    }

    @TestConfiguration
    static class Config {

        @Bean
        MeterRegistry meterRegistry() {
            return new SimpleMeterRegistry();
        }

        @Bean
        RecordingUserEventSender userEventSender() {
            return new RecordingUserEventSender();
        }
    }
}
