package com.ktb.moyeota.domain.settlement.service;

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
import com.ktb.moyeota.domain.settlement.entity.MeterAnalysisStatus;
import com.ktb.moyeota.domain.settlement.entity.Settlement;
import com.ktb.moyeota.domain.settlement.event.MeterAnalysisRequestedEvent;
import com.ktb.moyeota.domain.user.entity.User;
import com.ktb.moyeota.global.config.ClockConfig;
import com.ktb.moyeota.global.external.ai.AiAnalysisClient;
import com.ktb.moyeota.global.external.s3.S3PresignerTestSupport;
import com.ktb.moyeota.global.websocket.UserEventSender;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import jakarta.persistence.EntityManager;
import java.time.LocalDateTime;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

@DataJpaTest
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@Import({MeterAnalysisService.class, MeterAnalysisResultWriter.class, SettlementService.class, ImageReadUrlIssuer.class, S3PresignerTestSupport.class,
        ClockConfig.class, MeterAnalysisRejectTest.Config.class})
@EnableConfigurationProperties(MeterAnalysisProperties.class)
class MeterAnalysisRejectTest {

    private static final LocalDateTime STARTED_AT = LocalDateTime.of(2026, 9, 7, 9, 0, 3);

    @MockitoBean
    private AiAnalysisClient aiAnalysisClient;

    @MockitoBean
    private UserEventSender userEventSender;

    @MockitoBean
    private NotificationCommandService notificationCommandService;

    @Autowired
    private MeterAnalysisService meterAnalysisService;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private PlatformTransactionManager transactionManager;

    private TransactionTemplate transactionTemplate;
    private Long settlementId;

    @BeforeEach
    void setUp() {
        transactionTemplate = new TransactionTemplate(transactionManager);
        settlementId = transactionTemplate.execute(status -> {
            User host = persist(bankAccountHolder("방장"));
            User rider = persist(bankAccountHolder("동승자"));
            Companion pot = persist(taxiPot(host, CompanionStatus.COMPLETED, 2));
            persist(ChatRoom.create(pot));
            persist(participant(pot, host, COMPLETED));
            persist(participant(pot, rider, COMPLETED));
            Settlement settlement = persist(Settlement.open(pot));
            ReflectionTestUtils.setField(settlement, "meterAnalysisStatus", MeterAnalysisStatus.ANALYZING);
            ReflectionTestUtils.setField(settlement, "analysisTrialCount", 1);
            ReflectionTestUtils.setField(settlement, "analysisStartedAt", STARTED_AT);
            return settlement.getId();
        });
    }

    @AfterEach
    void cleanUp() {
        transactionTemplate.executeWithoutResult(status -> {
            for (String entity : new String[]{"SettlementParticipant", "Settlement", "CompanionParticipant",
                    "ChatRoom", "Companion", "User"}) {
                entityManager.createQuery("delete from " + entity).executeUpdate();
            }
        });
    }

    @Test
    @DisplayName("분석을 시작하기 전에 대기열이 가득 차 거절되면, 바깥 트랜잭션이 저장되지 않아도 실패로 저장하고 기회를 돌려준다")
    void rejectedBusy() {
        transactionTemplate.executeWithoutResult(status -> {
            status.flush();
            meterAnalysisService.rejectBusy(
                    new MeterAnalysisRequestedEvent(settlementId, "meter/a.jpg", STARTED_AT));
            status.setRollbackOnly();
        });

        Settlement saved = transactionTemplate.execute(status -> entityManager.find(Settlement.class, settlementId));
        assertThat(saved.getMeterAnalysisStatus()).isEqualTo(MeterAnalysisStatus.FAILED);
        assertThat(saved.getAnalysisTrialCount()).isZero();
    }

    private <T> T persist(T entity) {
        entityManager.persist(entity);
        return entity;
    }

    @TestConfiguration
    static class Config {

        @Bean
        MeterRegistry meterRegistry() {
            return new SimpleMeterRegistry();
        }
    }
}
