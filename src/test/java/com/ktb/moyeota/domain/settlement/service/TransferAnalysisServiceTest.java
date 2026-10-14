package com.ktb.moyeota.domain.settlement.service;

import static com.ktb.moyeota.domain.chat.entity.OutcomeStatus.COMPLETED;
import static com.ktb.moyeota.fixture.CompanionFixture.taxiPot;
import static com.ktb.moyeota.fixture.ParticipantFixture.participant;
import static com.ktb.moyeota.fixture.UserFixture.bankAccountHolder;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verifyNoInteractions;

import com.ktb.moyeota.domain.chat.entity.ChatRoom;
import com.ktb.moyeota.domain.companion.entity.Companion;
import com.ktb.moyeota.domain.companion.entity.CompanionStatus;
import com.ktb.moyeota.domain.image.service.ImageReadUrlIssuer;
import com.ktb.moyeota.domain.settlement.config.TransferAnalysisProperties;
import com.ktb.moyeota.domain.settlement.dto.TransferAnalyzedMessage;
import com.ktb.moyeota.domain.settlement.entity.MeterAnalysisStatus;
import com.ktb.moyeota.domain.settlement.entity.Settlement;
import com.ktb.moyeota.domain.settlement.entity.SettlementParticipant;
import com.ktb.moyeota.domain.settlement.entity.SettlementStatus;
import com.ktb.moyeota.domain.settlement.entity.TransferAnalysisStatus;
import com.ktb.moyeota.domain.settlement.event.TransferAnalysisRequestedEvent;
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
@Import({TransferAnalysisService.class, TransferAnalysisResultWriter.class, ImageReadUrlIssuer.class,
        S3PresignerTestSupport.class, TransferAnalysisServiceTest.Config.class})
@EnableConfigurationProperties(TransferAnalysisProperties.class)
class TransferAnalysisServiceTest {

    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");
    private static final LocalDateTime REQUESTED_AT = LocalDateTime.of(2026, 9, 7, 9, 10, 3);
    private static final LocalDateTime NOW = REQUESTED_AT.plusSeconds(5);
    private static final String TRANSFER_KEY = "transfer/2aac932d-f68b-4491-9891-fd47671fcf02.jpg";

    @MockitoBean
    private AiAnalysisClient aiAnalysisClient;

    @Autowired
    private RecordingUserEventSender userEventSender;

    @Autowired
    private TransferAnalysisService transferAnalysisService;

    @Autowired
    private TestEntityManager entityManager;

    private User host;
    private User rider;
    private Settlement settlement;
    private SettlementParticipant charge;

    @BeforeEach
    void setUp() {
        userEventSender.clear();
        host = entityManager.persist(bankAccountHolder("방장"));
        rider = entityManager.persist(bankAccountHolder("동승자"));
        Companion pot = entityManager.persist(taxiPot(host, CompanionStatus.COMPLETED, 2));
        entityManager.persist(ChatRoom.create(pot));
        entityManager.persist(participant(pot, host, COMPLETED));
        entityManager.persist(participant(pot, rider, COMPLETED));
        settlement = Settlement.open(pot);
        ReflectionTestUtils.setField(settlement, "status", SettlementStatus.REQUESTED);
        ReflectionTestUtils.setField(settlement, "meterAnalysisStatus", MeterAnalysisStatus.MANUAL);
        ReflectionTestUtils.setField(settlement, "totalAmount", 12000);
        entityManager.persist(settlement);
        charge = SettlementParticipant.charge(settlement, rider, 6000);
        ReflectionTestUtils.setField(charge, "transferImageUrl", TRANSFER_KEY);
        ReflectionTestUtils.setField(charge, "analysisStatus", TransferAnalysisStatus.ANALYZING);
        ReflectionTestUtils.setField(charge, "transferImageAnalysisTrialCount", 1);
        entityManager.persist(charge);
        flushAndClear();
    }

    @Test
    @DisplayName("AI가 읽은 금액이 내 몫과 같으면 PASSED · 송금 확인으로 바꾸고, 제출한 동승자와 방장에게 알린다")
    void amountMatched() {
        given(aiAnalysisClient.readTransfer(argThat(url -> url.contains(TRANSFER_KEY) && url.contains("X-Amz-Expires=1800"))))
                .willReturn(new AnalysisOutcome.Read<>(6000));

        transferAnalysisService.analyze(request(1));

        SettlementParticipant saved = reloadCharge();
        assertThat(saved.getAnalysisStatus()).isEqualTo(TransferAnalysisStatus.PASSED);
        assertThat(saved.isTransferCompleted()).isTrue();
        assertThat(userEventSender.sent()).containsExactly(
                sentTo(rider, "PASSED", true, 2), sentTo(host, "PASSED", true, 2));
    }

    @Test
    @DisplayName("AI가 읽은 금액이 내 몫과 다르면 PASSED이지만 송금 확인은 하지 않는다")
    void amountMismatched() {
        given(aiAnalysisClient.readTransfer(any())).willReturn(new AnalysisOutcome.Read<>(5000));

        transferAnalysisService.analyze(request(1));

        SettlementParticipant saved = reloadCharge();
        assertThat(saved.getAnalysisStatus()).isEqualTo(TransferAnalysisStatus.PASSED);
        assertThat(saved.isTransferCompleted()).isFalse();
        assertThat(saved.getTransferImageAnalysisTrialCount()).isEqualTo(1);
        assertThat(userEventSender.sent()).containsExactly(
                sentTo(rider, "PASSED", false, 2), sentTo(host, "PASSED", false, 2));
    }

    @Test
    @DisplayName("이체 완료로 인정되지 않은 사진이면 FAILED이고 기회는 쓴 것으로 친다")
    void unreadable() {
        given(aiAnalysisClient.readTransfer(any())).willReturn(new AnalysisOutcome.Unreadable<>("JUDGE_PARSE_FAILED"));

        transferAnalysisService.analyze(request(1));

        assertFailed(1);
        assertThat(userEventSender.sent()).containsExactly(
                sentTo(rider, "FAILED", false, 2), sentTo(host, "FAILED", false, 2));
    }

    @Test
    @DisplayName("AI 서버 문제로 실패해도 FAILED이고 기회는 쓴 것으로 친다")
    void systemFailure() {
        given(aiAnalysisClient.readTransfer(any())).willReturn(new AnalysisOutcome.SystemFailure<>("MODEL_NOT_READY"));

        transferAnalysisService.analyze(request(1));

        assertFailed(1);
        assertThat(userEventSender.sent()).containsExactly(
                sentTo(rider, "FAILED", false, 2), sentTo(host, "FAILED", false, 2));
    }

    @Test
    @DisplayName("세 번째 제출도 확인되지 않으면 남은 횟수 0으로 알린다")
    void lastAttemptFailed() {
        givenAnalyzing(3);
        given(aiAnalysisClient.readTransfer(any())).willReturn(new AnalysisOutcome.Read<>(5000));

        transferAnalysisService.analyze(request(3));

        assertThat(userEventSender.sent()).containsExactly(
                sentTo(rider, "PASSED", false, 0), sentTo(host, "PASSED", false, 0));
    }

    @Test
    @DisplayName("차례를 대기 상한보다 오래 기다렸으면 AI를 부르지 않고 FAILED로 끝낸다")
    void queueWaitExceeded() {
        transferAnalysisService.analyze(new TransferAnalysisRequestedEvent(
                settlement.getId(), rider.getId(), TRANSFER_KEY, 1, NOW.minusSeconds(91)));

        assertFailed(1);
        verifyNoInteractions(aiAnalysisClient);
    }

    @Test
    @DisplayName("다시 낸 사진을 분석 중이면 이전 제출의 늦은 결과는 버리고 알리지 않는다")
    void ignoresPreviousSubmission() {
        givenAnalyzing(2);
        given(aiAnalysisClient.readTransfer(any())).willReturn(new AnalysisOutcome.Read<>(6000));

        transferAnalysisService.analyze(request(1));

        SettlementParticipant saved = reloadCharge();
        assertThat(saved.getAnalysisStatus()).isEqualTo(TransferAnalysisStatus.ANALYZING);
        assertThat(saved.isTransferCompleted()).isFalse();
        assertThat(userEventSender.sent()).isEmpty();
    }

    @Test
    @DisplayName("분석 중인 사진이 없으면 결과를 버리고 알리지 않는다")
    void ignoresWhenNotAnalyzing() {
        SettlementParticipant managed = entityManager.find(SettlementParticipant.class, charge.getId());
        ReflectionTestUtils.setField(managed, "analysisStatus", TransferAnalysisStatus.FAILED);
        flushAndClear();
        given(aiAnalysisClient.readTransfer(any())).willReturn(new AnalysisOutcome.Read<>(6000));

        transferAnalysisService.analyze(request(1));

        assertThat(reloadCharge().getAnalysisStatus()).isEqualTo(TransferAnalysisStatus.FAILED);
        assertThat(userEventSender.sent()).isEmpty();
    }

    private RecordingUserEventSender.Sent sentTo(
            User recipient, String memberAnalysisStatus, boolean transferCompleted, int remainingTransferAttempts) {
        return new RecordingUserEventSender.Sent(recipient.getId(), UserChannel.SETTLEMENT, new TransferAnalyzedMessage(
                "TRANSFER_ANALYZED", settlement.getId(), rider.getId(), memberAnalysisStatus, transferCompleted,
                remainingTransferAttempts));
    }

    private TransferAnalysisRequestedEvent request(int attempt) {
        return new TransferAnalysisRequestedEvent(settlement.getId(), rider.getId(), TRANSFER_KEY, attempt, REQUESTED_AT);
    }

    private void givenAnalyzing(int trialCount) {
        SettlementParticipant managed = entityManager.find(SettlementParticipant.class, charge.getId());
        ReflectionTestUtils.setField(managed, "transferImageAnalysisTrialCount", trialCount);
        flushAndClear();
    }

    private void assertFailed(int trialCount) {
        SettlementParticipant saved = reloadCharge();
        assertThat(saved.getAnalysisStatus()).isEqualTo(TransferAnalysisStatus.FAILED);
        assertThat(saved.isTransferCompleted()).isFalse();
        assertThat(saved.getTransferImageAnalysisTrialCount()).isEqualTo(trialCount);
    }

    private SettlementParticipant reloadCharge() {
        flushAndClear();
        return entityManager.find(SettlementParticipant.class, charge.getId());
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
