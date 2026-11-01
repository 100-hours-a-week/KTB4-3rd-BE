package com.ktb.moyeota.domain.taxipot.service;

import static com.ktb.moyeota.domain.chat.entity.OutcomeStatus.PENDING;
import static com.ktb.moyeota.domain.companion.entity.CompanionStatus.RECRUITING;
import static com.ktb.moyeota.fixture.CompanionFixture.DEPARTURE_AT;
import static com.ktb.moyeota.fixture.CompanionFixture.ORIGIN_LAT;
import static com.ktb.moyeota.fixture.CompanionFixture.ORIGIN_LNG;
import static com.ktb.moyeota.fixture.CompanionFixture.taxiPot;
import static com.ktb.moyeota.fixture.MatchRequestFixture.matchingRequest;
import static com.ktb.moyeota.fixture.ParticipantFixture.participant;
import static com.ktb.moyeota.fixture.TaxiPotFixture.startCommand;
import static com.ktb.moyeota.fixture.UserFixture.bankAccountHolder;
import static org.assertj.core.api.Assertions.assertThat;

import com.ktb.moyeota.domain.chat.entity.ChatRoom;
import com.ktb.moyeota.domain.chat.service.ChatSystemMessageService;
import com.ktb.moyeota.domain.companion.entity.Companion;
import com.ktb.moyeota.domain.taxipot.entity.TaxiPotMatchRequest;
import com.ktb.moyeota.domain.taxipot.event.TaxiPotRideStartedEvent;
import com.ktb.moyeota.domain.taxipot.matching.GeoPoint;
import com.ktb.moyeota.domain.taxipot.matching.RegionGrid;
import com.ktb.moyeota.domain.taxipot.message.MatchingMessages;
import com.ktb.moyeota.domain.taxipot.repository.TaxiPotMatchRequestRepository;
import com.ktb.moyeota.domain.user.entity.User;
import com.ktb.moyeota.global.event.EventEnvelopeFactory;
import com.ktb.moyeota.global.outbox.entity.OutboxEvent;
import com.ktb.moyeota.global.outbox.repository.OutboxEventRepository;
import com.ktb.moyeota.global.outbox.service.OutboxRecorder;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jackson.autoconfigure.JacksonAutoConfiguration;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

@DataJpaTest
@ImportAutoConfiguration(JacksonAutoConfiguration.class)
@Import({TaxiPotService.class, ChatSystemMessageService.class, MatchingOutboxListener.class,
        OutboxRecorder.class, EventEnvelopeFactory.class, MatchingOutboxListenerTest.FixedClock.class})
@TestPropertySource(properties = "moyeota.taxi-pot.matching.mode=kafka")
class MatchingOutboxListenerTest {

    private static final LocalDateTime NOW = DEPARTURE_AT.minusHours(1);
    private static final String FIXTURE_CELL_KEY = "16871:63599";

    @Autowired
    private TaxiPotService taxiPotService;

    @Autowired
    private TaxiPotMatchRequestRepository taxiPotMatchRequestRepository;

    @Autowired
    private OutboxEventRepository outboxEventRepository;

    @Autowired
    private ApplicationEventPublisher eventPublisher;

    @Autowired
    private TestEntityManager entityManager;

    @Autowired
    private JsonMapper jsonMapper;

    @Test
    @DisplayName("매칭을 요청하면 같은 트랜잭션에서 출발지 칸 키로 matching.requested 를 아웃박스에 기록한다")
    void recordsRequested() {
        User me = entityManager.persist(bankAccountHolder("요청자"));

        taxiPotService.start(me.getId(), startCommand(DEPARTURE_AT));

        TaxiPotMatchRequest request = taxiPotMatchRequestRepository.findAll().getFirst();
        OutboxEvent event = single(outbox());
        assertThat(event.getTopic()).isEqualTo(MatchingMessages.REQUEST_TOPIC);
        assertThat(event.getMessageKey()).isEqualTo(
                MatchingMessages.key(new RegionGrid(2_000).cellOf(GeoPoint.of(ORIGIN_LAT, ORIGIN_LNG))));
        assertThat(event.getEventType()).isEqualTo(MatchingMessages.REQUESTED);
        JsonNode data = payload(event);
        assertThat(data.get("request_id").asLong()).isEqualTo(request.getId());
        assertThat(data.get("user_id").asLong()).isEqualTo(me.getId());
        assertThat(data.get("origin").get("name").asString()).isEqualTo("판교역");
        assertThat(data.get("dest").get("name").asString()).isEqualTo("강남역");
        assertThat(data.get("departure_at")).isNotNull();
        assertThat(data.get("requested_at")).isNotNull();
    }

    @Test
    @DisplayName("매칭을 취소하면 같은 칸 키로 matching.canceled 를 기록한다")
    void recordsCanceled() {
        User me = entityManager.persist(bankAccountHolder("요청자"));
        taxiPotService.start(me.getId(), startCommand(DEPARTURE_AT));
        TaxiPotMatchRequest request = taxiPotMatchRequestRepository.findAll().getFirst();

        taxiPotService.cancelMatching(me.getId());

        List<OutboxEvent> events = outbox();
        assertThat(events).extracting(OutboxEvent::getEventType)
                .containsExactly(MatchingMessages.REQUESTED, MatchingMessages.CANCELED);
        OutboxEvent canceled = events.getLast();
        assertThat(canceled.getMessageKey()).isEqualTo(events.getFirst().getMessageKey());
        assertThat(payload(canceled).get("request_id").asLong()).isEqualTo(request.getId());
    }

    @Test
    @DisplayName("팟에서 나가면 나간 사람의 매칭 요청 번호로 matching.left 를 기록한다")
    void recordsLeft() {
        User host = entityManager.persist(bankAccountHolder("방장"));
        User member = entityManager.persist(bankAccountHolder("동승자"));
        Companion pot = persistPot(host, member);
        persistMatched(host, pot);
        TaxiPotMatchRequest memberRequest = persistMatched(member, pot);

        taxiPotService.leave(member.getId(), pot.getId());

        OutboxEvent event = single(outbox());
        assertThat(event.getEventType()).isEqualTo(MatchingMessages.LEFT);
        assertThat(event.getMessageKey()).isEqualTo(FIXTURE_CELL_KEY);
        assertThat(payload(event).get("request_id").asLong()).isEqualTo(memberRequest.getId());
    }

    @Test
    @DisplayName("운행을 시작하면 팟 멤버의 매칭 요청 번호들로 matching.ride-started 를 기록한다")
    void recordsRideStarted() {
        User host = entityManager.persist(bankAccountHolder("방장"));
        User member = entityManager.persist(bankAccountHolder("동승자"));
        Companion pot = persistPot(host, member);
        TaxiPotMatchRequest hostRequest = persistMatched(host, pot);
        TaxiPotMatchRequest memberRequest = persistMatched(member, pot);

        eventPublisher.publishEvent(new TaxiPotRideStartedEvent(pot.getId(), NOW, null, null, null, null));

        OutboxEvent event = single(outbox());
        assertThat(event.getEventType()).isEqualTo(MatchingMessages.RIDE_STARTED);
        assertThat(event.getMessageKey()).isEqualTo(FIXTURE_CELL_KEY);
        assertThat(List.of(jsonMapper.treeToValue(payload(event).get("request_ids"), Long[].class)))
                .containsExactly(hostRequest.getId(), memberRequest.getId());
    }

    private Companion persistPot(User host, User member) {
        Companion pot = entityManager.persist(taxiPot(host, RECRUITING, 2));
        entityManager.persist(participant(pot, host, PENDING));
        entityManager.persist(participant(pot, member, PENDING));
        entityManager.persist(ChatRoom.create(pot));
        return pot;
    }

    private TaxiPotMatchRequest persistMatched(User user, Companion pot) {
        TaxiPotMatchRequest request = matchingRequest(user, "판교역", "강남역", DEPARTURE_AT);
        request.matchTo(pot);
        return entityManager.persistAndFlush(request);
    }

    private List<OutboxEvent> outbox() {
        entityManager.flush();
        return outboxEventRepository.findAll().stream()
                .sorted((a, b) -> Long.compare(a.getId(), b.getId()))
                .toList();
    }

    private static OutboxEvent single(List<OutboxEvent> events) {
        assertThat(events).hasSize(1);
        return events.getFirst();
    }

    private JsonNode payload(OutboxEvent event) {
        return jsonMapper.readTree(event.getPayload());
    }

    @TestConfiguration
    static class FixedClock {

        @Bean
        Clock clock() {
            ZoneId kst = ZoneId.of("Asia/Seoul");
            return Clock.fixed(NOW.atZone(kst).toInstant(), kst);
        }
    }
}
