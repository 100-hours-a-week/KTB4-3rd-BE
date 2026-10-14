package com.ktb.moyeota.domain.settlement.service;

import static com.ktb.moyeota.domain.chat.entity.OutcomeStatus.COMPLETED;
import static com.ktb.moyeota.fixture.CompanionFixture.taxiPot;
import static com.ktb.moyeota.fixture.ParticipantFixture.participant;
import static com.ktb.moyeota.fixture.UserFixture.bankAccountHolder;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.BDDMockito.given;

import com.ktb.moyeota.domain.chat.entity.ChatRoom;
import com.ktb.moyeota.domain.companion.entity.Companion;
import com.ktb.moyeota.domain.companion.entity.CompanionStatus;
import com.ktb.moyeota.domain.image.service.ImageReadUrlIssuer;
import com.ktb.moyeota.domain.notification.service.NotificationCommandService;
import com.ktb.moyeota.domain.settlement.config.MeterAnalysisProperties;
import com.ktb.moyeota.domain.settlement.dto.MeterAnalysisFinishedMessage;
import com.ktb.moyeota.domain.settlement.entity.MeterAnalysisStatus;
import com.ktb.moyeota.domain.settlement.entity.Settlement;
import com.ktb.moyeota.domain.settlement.entity.SettlementParticipant;
import com.ktb.moyeota.domain.settlement.entity.SettlementStatus;
import com.ktb.moyeota.domain.settlement.event.MeterAnalysisRequestedEvent;
import com.ktb.moyeota.domain.user.entity.User;
import com.ktb.moyeota.global.external.ai.AiAnalysisClient;
import com.ktb.moyeota.global.external.ai.AnalysisOutcome;
import com.ktb.moyeota.global.external.s3.S3PresignerTestSupport;
import com.ktb.moyeota.global.websocket.RecordingUserEventSender;
import com.ktb.moyeota.global.websocket.UserChannel;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
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
@Import({MeterAnalysisService.class, MeterAnalysisResultWriter.class, SettlementService.class, ImageReadUrlIssuer.class, S3PresignerTestSupport.class,
        MeterAnalysisServiceTest.Config.class})
@EnableConfigurationProperties(MeterAnalysisProperties.class)
class MeterAnalysisServiceTest {

    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");
    private static final LocalDateTime STARTED_AT = LocalDateTime.of(2026, 9, 7, 9, 0, 3);
    private static final LocalDateTime NOW = STARTED_AT.plusSeconds(5);
    private static final String METER_KEY = "meter/2aac932d-f68b-4491-9891-fd47671fcf02.jpg";

    @MockitoBean
    private AiAnalysisClient aiAnalysisClient;

    @Autowired
    private RecordingUserEventSender userEventSender;

    @MockitoBean
    private NotificationCommandService notificationCommandService;

    @Autowired
    private MeterAnalysisService meterAnalysisService;

    @Autowired
    private TestEntityManager entityManager;

    private User host;
    private User rider;
    private Settlement settlement;

    @BeforeEach
    void setUp() {
        userEventSender.clear();
        host = entityManager.persist(bankAccountHolder("방장"));
        rider = entityManager.persist(bankAccountHolder("동승자"));
        Companion pot = entityManager.persist(taxiPot(host, CompanionStatus.COMPLETED, 2));
        entityManager.persist(ChatRoom.create(pot));
        entityManager.persist(participant(pot, host, COMPLETED));
        entityManager.persist(participant(pot, rider, COMPLETED));
        settlement = entityManager.persist(Settlement.open(pot));
        ReflectionTestUtils.setField(settlement, "meterImageUrl", METER_KEY);
        ReflectionTestUtils.setField(settlement, "meterAnalysisStatus", MeterAnalysisStatus.ANALYZING);
        ReflectionTestUtils.setField(settlement, "analysisTrialCount", 1);
        ReflectionTestUtils.setField(settlement, "analysisStartedAt", STARTED_AT);
        flushAndClear();
    }

    @Test
    @DisplayName("택시비 금액을 읽으면 그 금액으로 청구 단계가 되고 동승자에게 청구하며, 방장에게 택시비 이미지 분석의 종료를 알린다")
    void read() {
        given(aiAnalysisClient.readMeter(argThat(url -> url.contains(METER_KEY) && url.contains("X-Amz-Expires=1800"))))
                .willReturn(new AnalysisOutcome.Read<>(18000));

        meterAnalysisService.analyze(request());

        Settlement saved = reload();
        assertThat(saved.getStatus()).isEqualTo(SettlementStatus.REQUESTED);
        assertThat(saved.getMeterAnalysisStatus()).isEqualTo(MeterAnalysisStatus.PASSED);
        assertThat(saved.getTotalAmount()).isEqualTo(18000);
        assertThat(charges()).singleElement().satisfies(charge -> {
            assertThat(charge.getUser().getId()).isEqualTo(rider.getId());
            assertThat(charge.getAmount()).isEqualTo(9000);
        });
        assertThat(userEventSender.sent()).containsExactly(sentToHost("PASSED", 1));
    }

    @Test
    @DisplayName("사진 때문에 못 읽으면 실패이고 기회는 쓴 것으로 친다")
    void unreadable() {
        given(aiAnalysisClient.readMeter(any())).willReturn(new AnalysisOutcome.Unreadable<>("JUDGE_PARSE_FAILED"));

        meterAnalysisService.analyze(request());

        assertFailed(1);
        assertThat(userEventSender.sent()).containsExactly(sentToHost("FAILED", 1));
    }

    @Test
    @DisplayName("시스템 문제로 실패하면 기회를 돌려준다")
    void systemFailure() {
        given(aiAnalysisClient.readMeter(any())).willReturn(new AnalysisOutcome.SystemFailure<>("MODEL_NOT_READY"));

        meterAnalysisService.analyze(request());

        assertFailed(0);
        assertThat(userEventSender.sent()).containsExactly(sentToHost("FAILED", 2));
    }

    @Test
    @DisplayName("읽은 금액이 1원 ~ 999,999원 밖이면 사진 문제로 실패한다")
    void amountOutOfRange() {
        given(aiAnalysisClient.readMeter(any())).willReturn(new AnalysisOutcome.Read<>(0));

        meterAnalysisService.analyze(request());

        assertFailed(1);
        assertThat(reload().getTotalAmount()).isNull();
    }

    @Test
    @DisplayName("차례를 대기 상한보다 오래 기다렸으면 AI 결과와 상관없이 시스템 문제로 실패하고 기회를 돌려준다")
    void queueWaitExceeded() {
        ReflectionTestUtils.setField(settlement, "analysisStartedAt", STARTED_AT.minusSeconds(91));
        entityManager.merge(settlement);
        flushAndClear();
        given(aiAnalysisClient.readMeter(any())).willReturn(new AnalysisOutcome.Read<>(18000));

        meterAnalysisService.analyze(new MeterAnalysisRequestedEvent(
                settlement.getId(), METER_KEY, STARTED_AT.minusSeconds(91)));

        assertFailed(0);
        assertThat(reload().getTotalAmount()).isNull();
    }

    @Test
    @DisplayName("분석 중에 방장이 금액을 직접 입력했으면 늦게 온 결과는 버리고 알리지 않는다")
    void ignoresAfterManualInput() {
        ReflectionTestUtils.setField(settlement, "meterAnalysisStatus", MeterAnalysisStatus.MANUAL);
        ReflectionTestUtils.setField(settlement, "status", SettlementStatus.REQUESTED);
        ReflectionTestUtils.setField(settlement, "totalAmount", 10000);
        entityManager.merge(settlement);
        flushAndClear();
        given(aiAnalysisClient.readMeter(any())).willReturn(new AnalysisOutcome.Read<>(18000));

        meterAnalysisService.analyze(request());

        Settlement saved = reload();
        assertThat(saved.getMeterAnalysisStatus()).isEqualTo(MeterAnalysisStatus.MANUAL);
        assertThat(saved.getTotalAmount()).isEqualTo(10000);
        assertThat(userEventSender.sent()).isEmpty();
    }

    @Test
    @DisplayName("다시 낸 사진을 분석 중이면 이전 제출의 늦은 결과는 버린다")
    void ignoresPreviousSubmission() {
        given(aiAnalysisClient.readMeter(any())).willReturn(new AnalysisOutcome.Read<>(18000));

        meterAnalysisService.analyze(new MeterAnalysisRequestedEvent(
                settlement.getId(), METER_KEY, STARTED_AT.minusSeconds(30)));

        Settlement saved = reload();
        assertThat(saved.getMeterAnalysisStatus()).isEqualTo(MeterAnalysisStatus.ANALYZING);
        assertThat(saved.getTotalAmount()).isNull();
        assertThat(userEventSender.sent()).isEmpty();
    }

    private RecordingUserEventSender.Sent sentToHost(String meterAnalysisStatus, int remainingMeterAttempts) {
        return new RecordingUserEventSender.Sent(host.getId(), UserChannel.SETTLEMENT, new MeterAnalysisFinishedMessage(
                "METER_ANALYSIS_FINISHED", settlement.getId(), meterAnalysisStatus, remainingMeterAttempts));
    }

    private MeterAnalysisRequestedEvent request() {
        return new MeterAnalysisRequestedEvent(settlement.getId(), METER_KEY, STARTED_AT);
    }

    private void assertFailed(int trialCount) {
        Settlement saved = reload();
        assertThat(saved.getStatus()).isEqualTo(SettlementStatus.PENDING);
        assertThat(saved.getMeterAnalysisStatus()).isEqualTo(MeterAnalysisStatus.FAILED);
        assertThat(saved.getAnalysisTrialCount()).isEqualTo(trialCount);
        assertThat(charges()).isEmpty();
    }

    private Settlement reload() {
        flushAndClear();
        return entityManager.find(Settlement.class, settlement.getId());
    }

    private List<SettlementParticipant> charges() {
        return entityManager.getEntityManager()
                .createQuery("select p from SettlementParticipant p join fetch p.user where p.settlement.id = :id",
                        SettlementParticipant.class)
                .setParameter("id", settlement.getId())
                .getResultList();
    }

    private void flushAndClear() {
        entityManager.flush();
        entityManager.clear();
    }

    @TestConfiguration
    static class Config {

        @Bean
        Clock clock() {
            return Clock.fixed(NOW.atZone(SEOUL).toInstant(), SEOUL);
        }

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
