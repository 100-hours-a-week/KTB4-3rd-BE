package com.ktb.moyeota.domain.settlement.service;

import static com.ktb.moyeota.domain.chat.entity.OutcomeStatus.COMPLETED;
import static com.ktb.moyeota.fixture.CompanionFixture.taxiPot;
import static com.ktb.moyeota.fixture.ParticipantFixture.participant;
import static com.ktb.moyeota.fixture.UserFixture.bankAccountHolder;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

import com.ktb.moyeota.domain.chat.entity.ChatRoom;
import com.ktb.moyeota.domain.companion.entity.Companion;
import com.ktb.moyeota.domain.companion.entity.CompanionStatus;
import com.ktb.moyeota.domain.image.model.ImagePurpose;
import com.ktb.moyeota.domain.image.model.UploadScope;
import com.ktb.moyeota.domain.image.service.ImagePromotionService;
import com.ktb.moyeota.domain.image.service.ImageReadUrlIssuer;
import com.ktb.moyeota.domain.settlement.entity.MeterAnalysisStatus;
import com.ktb.moyeota.domain.settlement.entity.Settlement;
import com.ktb.moyeota.domain.settlement.entity.SettlementParticipant;
import com.ktb.moyeota.domain.settlement.entity.SettlementStatus;
import com.ktb.moyeota.domain.settlement.message.ImageAnalysisMessages;
import com.ktb.moyeota.domain.user.entity.User;
import com.ktb.moyeota.global.config.MutableClock;
import com.ktb.moyeota.global.config.TestClockConfig;
import com.ktb.moyeota.global.event.EventEnvelopeFactory;
import com.ktb.moyeota.global.external.s3.S3PresignerTestSupport;
import com.ktb.moyeota.global.outbox.entity.OutboxEvent;
import com.ktb.moyeota.global.outbox.repository.OutboxEventRepository;
import com.ktb.moyeota.global.outbox.service.OutboxRecorder;
import java.time.LocalDateTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jackson.autoconfigure.JacksonAutoConfiguration;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.util.ReflectionTestUtils;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

@DataJpaTest
@ImportAutoConfiguration(JacksonAutoConfiguration.class)
@Import({TransferProofSubmissionService.class, TransferAnalysisOutboxListener.class, OutboxRecorder.class,
        EventEnvelopeFactory.class, ImageReadUrlIssuer.class, S3PresignerTestSupport.class, TestClockConfig.class})
@TestPropertySource(properties = "moyeota.settlement.meter-analysis.mode=kafka")
class TransferAnalysisOutboxListenerTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 9, 7, 9, 10, 3);
    private static final String FILE_NAME = "2aac932d-f68b-4491-9891-fd47671fcf02.jpg";
    private static final String TRANSFER_KEY = "transfer/" + FILE_NAME;

    @MockitoBean
    private ImagePromotionService imagePromotionService;

    @Autowired
    private MutableClock clock;

    @Autowired
    private TransferProofSubmissionService transferProofSubmissionService;

    @Autowired
    private OutboxEventRepository outboxEventRepository;

    @Autowired
    private TestEntityManager entityManager;

    @Autowired
    private JsonMapper jsonMapper;

    private User rider;
    private Settlement settlement;
    private String tmpKey;

    @BeforeEach
    void setUp() {
        clock.set(NOW);
        User host = entityManager.persist(bankAccountHolder("방장"));
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
        entityManager.persistAndFlush(SettlementParticipant.charge(settlement, rider, 6000));
        tmpKey = "tmp/transfer/u-" + rider.getId() + "/" + FILE_NAME;
        given(imagePromotionService.promote(UploadScope.user(rider.getId()), ImagePurpose.TRANSFER, tmpKey))
                .willReturn(TRANSFER_KEY);
    }

    @Test
    @DisplayName("kafka 방식이면 사진을 낸 트랜잭션에서 정산 · 동승자 키로 송금 분석 요청을 아웃박스에 적는다")
    void recordsTransferRequest() {
        transferProofSubmissionService.submit(rider.getId(), settlement.getId(), tmpKey);

        assertThat(outboxEventRepository.findAll()).singleElement().satisfies(event -> {
            assertThat(event.getTopic()).isEqualTo(ImageAnalysisMessages.REQUEST_TOPIC);
            assertThat(event.getMessageKey()).isEqualTo(settlement.getId() + ":" + rider.getId());
            assertThat(event.getEventType()).isEqualTo(ImageAnalysisMessages.TRANSFER_REQUESTED);
            assertThat(event.getSchemaVersion()).isEqualTo(ImageAnalysisMessages.TRANSFER_SCHEMA_VERSION);
        });
    }

    @Test
    @DisplayName("요청에는 동승자와 몇 번째 제출인지를 싣고, 확정 키 대신 30분짜리 읽기 주소를 싣는다")
    void requestData() {
        transferProofSubmissionService.submit(rider.getId(), settlement.getId(), tmpKey);

        JsonNode data = payload(outboxEventRepository.findAll().getFirst());
        assertThat(data.get("settlement_id").asLong()).isEqualTo(settlement.getId());
        assertThat(data.get("user_id").asLong()).isEqualTo(rider.getId());
        assertThat(data.get("attempt").asInt()).isEqualTo(1);
        assertThat(data.get("image_url").asString()).contains(TRANSFER_KEY).contains("X-Amz-Expires=1800");
    }

    private JsonNode payload(OutboxEvent event) {
        return jsonMapper.readTree(event.getPayload());
    }
}
