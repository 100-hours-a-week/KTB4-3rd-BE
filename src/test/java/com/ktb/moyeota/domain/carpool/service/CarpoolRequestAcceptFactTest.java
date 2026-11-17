package com.ktb.moyeota.domain.carpool.service;

import static com.ktb.moyeota.domain.carpool.entity.CompanionRequestStatus.ACCEPTED;
import static com.ktb.moyeota.domain.companion.entity.CompanionStatus.RECRUITING;
import static com.ktb.moyeota.fixture.CompanionFixture.DEPARTURE_AT;
import static com.ktb.moyeota.fixture.CompanionFixture.carpool;
import static com.ktb.moyeota.fixture.ParticipantFixture.participant;
import static com.ktb.moyeota.fixture.UserFixture.user;
import static org.assertj.core.api.Assertions.assertThat;

import com.ktb.moyeota.domain.carpool.entity.CompanionRequest;
import com.ktb.moyeota.domain.chat.entity.ChatRoom;
import com.ktb.moyeota.domain.chat.entity.Message;
import com.ktb.moyeota.domain.chat.entity.OutcomeStatus;
import com.ktb.moyeota.domain.companion.entity.Companion;
import com.ktb.moyeota.domain.companion.message.CompanionChangedMessages;
import com.ktb.moyeota.domain.companion.service.CompanionParticipantOutboxListener;
import com.ktb.moyeota.domain.notification.service.NotificationCommandService;
import com.ktb.moyeota.domain.user.entity.User;
import com.ktb.moyeota.global.config.MutableClock;
import com.ktb.moyeota.global.config.TestClockConfig;
import com.ktb.moyeota.global.event.EventEnvelopeFactory;
import com.ktb.moyeota.global.outbox.repository.OutboxEventRepository;
import com.ktb.moyeota.global.outbox.service.OutboxRecorder;
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
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

@DataJpaTest
@ImportAutoConfiguration(JacksonAutoConfiguration.class)
@Import({CarpoolRequestService.class, CompanionParticipantOutboxListener.class, OutboxRecorder.class,
        EventEnvelopeFactory.class, TestClockConfig.class})
@TestPropertySource(properties = "moyeota.chat.system-message.mode=kafka")
class CarpoolRequestAcceptFactTest {

    @Autowired
    private MutableClock clock;

    @Autowired
    private CarpoolRequestService carpoolRequestService;

    @Autowired
    private OutboxEventRepository outboxEventRepository;

    @Autowired
    private TestEntityManager entityManager;

    @Autowired
    private JsonMapper jsonMapper;

    @MockitoBean
    private NotificationCommandService notificationCommandService;

    @Test
    @DisplayName("kafka 모드에서 동승 요청을 수락하면 입장 메시지를 바로 쓰지 않고 카풀 입장 사실을 아웃박스에 남긴다")
    void recordsJoinedFact() {
        clock.set(DEPARTURE_AT.minusHours(1));
        User host = entityManager.persist(user("방장"));
        User requester = entityManager.persist(user("요청자"));
        Companion carpool = entityManager.persist(carpool(host, RECRUITING, 1));
        entityManager.persist(participant(carpool, host, OutcomeStatus.PENDING));
        ChatRoom chatRoom = entityManager.persist(ChatRoom.create(carpool));
        CompanionRequest request = entityManager.persist(CompanionRequest.send(carpool, requester, "같이 가요"));
        entityManager.flush();

        carpoolRequestService.handle(host.getId(), carpool.getId(), request.getId(), ACCEPTED);

        entityManager.flush();
        assertThat(outboxEventRepository.findAll()).singleElement().satisfies(event -> {
            assertThat(event.getTopic()).isEqualTo(CompanionChangedMessages.TOPIC);
            assertThat(event.getMessageKey()).isEqualTo(String.valueOf(carpool.getId()));
            assertThat(event.getEventType()).isEqualTo(CompanionChangedMessages.PARTICIPANT_JOINED);
            JsonNode data = jsonMapper.readTree(event.getPayload());
            assertThat(data.get("kind").asString()).isEqualTo("CARPOOL");
            assertThat(data.get("user_id").asLong()).isEqualTo(requester.getId());
        });
        assertThat(entityManager.getEntityManager()
                .createQuery("SELECT m FROM Message m WHERE m.chatRoom.id = :id", Message.class)
                .setParameter("id", chatRoom.getId())
                .getResultList()).isEmpty();
    }
}
