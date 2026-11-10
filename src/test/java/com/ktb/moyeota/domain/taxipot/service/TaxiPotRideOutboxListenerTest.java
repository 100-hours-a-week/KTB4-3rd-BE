package com.ktb.moyeota.domain.taxipot.service;

import static com.ktb.moyeota.domain.chat.entity.OutcomeStatus.PENDING;
import static com.ktb.moyeota.domain.companion.entity.CompanionStatus.COMPLETED;
import static com.ktb.moyeota.domain.companion.entity.CompanionStatus.IN_PROGRESS;
import static com.ktb.moyeota.domain.companion.entity.CompanionStatus.RECRUITING;
import static com.ktb.moyeota.fixture.CompanionFixture.DEPARTURE_AT;
import static com.ktb.moyeota.fixture.CompanionFixture.taxiPot;
import static com.ktb.moyeota.fixture.ParticipantFixture.participant;
import static com.ktb.moyeota.fixture.UserFixture.bankAccountHolder;
import static org.assertj.core.api.Assertions.assertThat;

import com.ktb.moyeota.domain.chat.entity.ChatRoom;
import com.ktb.moyeota.domain.chat.entity.Message;
import com.ktb.moyeota.domain.chat.service.ChatSystemMessageService;
import com.ktb.moyeota.domain.companion.entity.Companion;
import com.ktb.moyeota.domain.companion.entity.CompanionStatus;
import com.ktb.moyeota.domain.companion.message.CompanionChangedMessages;
import com.ktb.moyeota.domain.user.entity.User;
import com.ktb.moyeota.global.config.MutableClock;
import com.ktb.moyeota.global.config.TestClockConfig;
import com.ktb.moyeota.global.event.EventEnvelopeFactory;
import com.ktb.moyeota.global.outbox.entity.OutboxEvent;
import com.ktb.moyeota.global.outbox.repository.OutboxEventRepository;
import com.ktb.moyeota.global.outbox.service.OutboxRecorder;
import java.util.List;
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
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

@DataJpaTest
@ImportAutoConfiguration(JacksonAutoConfiguration.class)
@Import({TaxiPotService.class, ChatSystemMessageService.class, TaxiPotRideOutboxListener.class,
        OutboxRecorder.class, EventEnvelopeFactory.class, TestClockConfig.class})
@TestPropertySource(properties = "moyeota.chat.system-message.mode=kafka")
class TaxiPotRideOutboxListenerTest {

    @Autowired
    private MutableClock clock;

    @Autowired
    private TaxiPotService taxiPotService;

    @Autowired
    private OutboxEventRepository outboxEventRepository;

    @Autowired
    private TestEntityManager entityManager;

    @Autowired
    private JsonMapper jsonMapper;

    @BeforeEach
    void fixClock() {
        clock.set(DEPARTURE_AT);
    }

    @Test
    @DisplayName("운행을 시작하면 채팅 메시지 대신 동행 바뀜 토픽에 운행 시작 사실을 남긴다")
    void recordsRideStarted() {
        Companion pot = persistTaxiPot(RECRUITING);

        taxiPotService.changeStatus(pot.getHost().getId(), pot.getId(), IN_PROGRESS);

        OutboxEvent recorded = onlyRecorded();
        assertThat(recorded.getTopic()).isEqualTo(CompanionChangedMessages.TOPIC);
        assertThat(recorded.getMessageKey()).isEqualTo(String.valueOf(pot.getId()));
        assertThat(recorded.getEventType()).isEqualTo(CompanionChangedMessages.RIDE_STARTED);
        assertThat(recorded.getSchemaVersion()).isEqualTo(CompanionChangedMessages.SCHEMA_VERSION);
        JsonNode data = jsonMapper.readTree(recorded.getPayload());
        assertThat(data.get("companion_id").asLong()).isEqualTo(pot.getId());
        assertThat(data.get("kind").asString()).isEqualTo("TAXI_POT");
        assertThat(messagesOf(pot)).isEmpty();
    }

    @Test
    @DisplayName("운행을 종료하면 채팅 메시지 대신 운행 종료 사실 하나만 남긴다")
    void recordsRideEnded() {
        Companion pot = persistTaxiPot(IN_PROGRESS);

        taxiPotService.changeStatus(pot.getHost().getId(), pot.getId(), COMPLETED);

        OutboxEvent recorded = onlyRecorded();
        assertThat(recorded.getEventType()).isEqualTo(CompanionChangedMessages.RIDE_ENDED);
        assertThat(recorded.getMessageKey()).isEqualTo(String.valueOf(pot.getId()));
        assertThat(messagesOf(pot)).isEmpty();
    }

    private Companion persistTaxiPot(CompanionStatus status) {
        User host = entityManager.persist(bankAccountHolder("방장"));
        Companion pot = entityManager.persist(taxiPot(host, status, 2));
        entityManager.persist(ChatRoom.create(pot));
        entityManager.persistAndFlush(participant(pot, host, PENDING));
        return pot;
    }

    private OutboxEvent onlyRecorded() {
        entityManager.flush();
        List<OutboxEvent> recorded = outboxEventRepository.findAll();
        assertThat(recorded).hasSize(1);
        return recorded.get(0);
    }

    private List<Message> messagesOf(Companion pot) {
        entityManager.flush();
        return entityManager.getEntityManager()
                .createQuery("SELECT m FROM Message m WHERE m.chatRoom.companion.id = :potId", Message.class)
                .setParameter("potId", pot.getId())
                .getResultList();
    }
}
