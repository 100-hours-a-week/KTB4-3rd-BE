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
import com.ktb.moyeota.domain.chat.entity.MessageType;
import com.ktb.moyeota.domain.companion.entity.Companion;
import com.ktb.moyeota.domain.companion.entity.CompanionStatus;
import com.ktb.moyeota.domain.image.service.ImagePromotionService;
import com.ktb.moyeota.domain.notification.service.NotificationCommandService;
import com.ktb.moyeota.domain.settlement.dto.MeterAnalysisFinishedMessage;
import com.ktb.moyeota.domain.settlement.entity.MeterAnalysisStatus;
import com.ktb.moyeota.domain.settlement.entity.Settlement;
import com.ktb.moyeota.domain.settlement.entity.SettlementStatus;
import com.ktb.moyeota.domain.user.entity.User;
import com.ktb.moyeota.global.crypto.AccountNoCipher;
import com.ktb.moyeota.global.external.ai.AiAnalysisClient;
import com.ktb.moyeota.global.external.ai.AnalysisOutcome;
import com.ktb.moyeota.global.websocket.RecordingUserEventSender;
import com.ktb.moyeota.global.websocket.UserChannel;
import jakarta.persistence.EntityManager;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.support.TransactionTemplate;

@SpringBootTest
class MeterAnalysisFlowIntegrationTest {

    private static final String METER_KEY = "meter/2aac932d-f68b-4491-9891-fd47671fcf02.jpg";

    static {
        System.setProperty("aws.accessKeyId", System.getProperty("aws.accessKeyId", "test-access-key"));
        System.setProperty("aws.secretAccessKey", System.getProperty("aws.secretAccessKey", "test-secret-key"));
    }

    @MockitoBean
    private ImagePromotionService imagePromotionService;

    @MockitoBean
    private AiAnalysisClient aiAnalysisClient;

    @MockitoBean
    private NotificationCommandService notificationCommandService;

    @Autowired
    private MeterImageService meterImageService;

    @Autowired
    private RecordingUserEventSender userEventSender;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private AccountNoCipher accountNoCipher;

    @Autowired
    private TransactionTemplate transactionTemplate;

    private Long hostId;
    private Long riderId;
    private Long settlementId;
    private String tmpKey;

    @BeforeEach
    void setUp() {
        userEventSender.clear();
        transactionTemplate.executeWithoutResult(status -> {
            User host = persist(bankAccountHolder("방장", accountNoCipher.encrypt("11012345678")));
            User rider = persist(bankAccountHolder("동승자"));
            Companion pot = persist(taxiPot(host, CompanionStatus.COMPLETED, 2));
            persist(ChatRoom.create(pot));
            persist(participant(pot, host, COMPLETED));
            persist(participant(pot, rider, COMPLETED));
            hostId = host.getId();
            riderId = rider.getId();
            settlementId = persist(Settlement.open(pot)).getId();
        });
        tmpKey = "tmp/meter/u-" + hostId + "/2aac932d-f68b-4491-9891-fd47671fcf02.jpg";
        given(imagePromotionService.promote(any(), any(), any())).willReturn(METER_KEY);
    }

    @AfterEach
    void cleanUp() {
        transactionTemplate.executeWithoutResult(status -> {
            entityManager.createNativeQuery("SET REFERENTIAL_INTEGRITY FALSE").executeUpdate();
            List<?> tables = entityManager.createNativeQuery(
                    "SELECT TABLE_NAME FROM INFORMATION_SCHEMA.TABLES WHERE TABLE_SCHEMA = 'PUBLIC'").getResultList();
            tables.forEach(table -> entityManager.createNativeQuery("TRUNCATE TABLE " + table).executeUpdate());
            entityManager.createNativeQuery("SET REFERENTIAL_INTEGRITY TRUE").executeUpdate();
        });
    }

    @Test
    @DisplayName("미터기 사진을 내면 커밋 뒤 분석 스레드에서 AI가 읽은 금액으로 청구 단계가 되고 방장에게 알린다")
    void submitThenReadOnAnalysisThread() throws InterruptedException {
        given(aiAnalysisClient.readMeter(argThat(url -> url.contains(METER_KEY) && url.contains("X-Amz-Expires=1800"))))
                .willReturn(new AnalysisOutcome.Read<>(18000));

        meterImageService.submit(hostId, settlementId, tmpKey);

        Settlement settled = awaitAnalysisFinished();
        assertThat(settled.getStatus()).isEqualTo(SettlementStatus.REQUESTED);
        assertThat(settled.getMeterAnalysisStatus()).isEqualTo(MeterAnalysisStatus.PASSED);
        assertThat(settled.getTotalAmount()).isEqualTo(18000);
        assertThat(chargedAmounts()).containsExactly(List.of(riderId, 9000));
        assertThat(systemMessageTypes()).contains(MessageType.SYSTEM_TRANSFER_REQUESTED);
        assertThat(userEventSender.sent()).containsExactly(new RecordingUserEventSender.Sent(hostId,
                UserChannel.SETTLEMENT,
                new MeterAnalysisFinishedMessage("METER_ANALYSIS_FINISHED", settlementId, "PASSED", 1)));
    }

    @Test
    @DisplayName("AI가 사진 때문에 못 읽으면 분석 스레드에서 실패로 바꾸고 방장에게 남은 기회와 함께 알린다")
    void submitThenUnreadableOnAnalysisThread() throws InterruptedException {
        given(aiAnalysisClient.readMeter(any())).willReturn(new AnalysisOutcome.Unreadable<>("JUDGE_PARSE_FAILED"));

        meterImageService.submit(hostId, settlementId, tmpKey);

        Settlement settled = awaitAnalysisFinished();
        assertThat(settled.getStatus()).isEqualTo(SettlementStatus.PENDING);
        assertThat(settled.getMeterAnalysisStatus()).isEqualTo(MeterAnalysisStatus.FAILED);
        assertThat(settled.getAnalysisTrialCount()).isEqualTo(1);
        assertThat(chargedAmounts()).isEmpty();
        assertThat(userEventSender.sent()).containsExactly(new RecordingUserEventSender.Sent(hostId,
                UserChannel.SETTLEMENT,
                new MeterAnalysisFinishedMessage("METER_ANALYSIS_FINISHED", settlementId, "FAILED", 1)));
    }

    private Settlement awaitAnalysisFinished() throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (true) {
            Settlement settlement = transactionTemplate.execute(
                    status -> entityManager.find(Settlement.class, settlementId));
            if (settlement.getMeterAnalysisStatus() != MeterAnalysisStatus.ANALYZING && !userEventSender.sent().isEmpty()) {
                return settlement;
            }
            assertThat(System.nanoTime()).as("분석 완료 대기 시간 초과").isLessThan(deadline);
            Thread.sleep(50);
        }
    }

    private List<List<Object>> chargedAmounts() {
        return transactionTemplate.execute(status -> entityManager.createQuery(
                        "select p.user.id, p.amount from SettlementParticipant p where p.settlement.id = :id",
                        Object[].class)
                .setParameter("id", settlementId)
                .getResultList()
                .stream()
                .map(List::of)
                .toList());
    }

    private List<MessageType> systemMessageTypes() {
        return transactionTemplate.execute(status -> entityManager.createQuery(
                        "select m.messageType from Message m", MessageType.class)
                .getResultList());
    }

    private <T> T persist(T entity) {
        entityManager.persist(entity);
        return entity;
    }

    @TestConfiguration
    static class Config {

        @Bean
        @Primary
        RecordingUserEventSender recordingUserEventSender() {
            return new RecordingUserEventSender();
        }
    }
}
