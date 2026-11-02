package com.ktb.moyeota.domain.taxipot;

import static com.ktb.moyeota.fixture.UserFixture.bankAccountHolder;
import static org.assertj.core.api.Assertions.assertThat;

import com.ktb.moyeota.domain.companion.entity.Companion;
import com.ktb.moyeota.domain.taxipot.consumer.MatchDecisionConsumer;
import com.ktb.moyeota.domain.taxipot.entity.TaxiPotMatchRequestStatus;
import com.ktb.moyeota.domain.taxipot.message.MatchingMessages;
import com.ktb.moyeota.domain.taxipot.model.TaxiPotStartCommand;
import com.ktb.moyeota.domain.taxipot.repository.TaxiPotMatchRequestRepository;
import com.ktb.moyeota.domain.taxipot.repository.TaxiPotRepository;
import com.ktb.moyeota.domain.taxipot.service.TaxiPotService;
import com.ktb.moyeota.domain.user.entity.User;
import com.ktb.moyeota.domain.user.repository.UserRepository;
import com.ktb.moyeota.global.config.KafkaTestContainerConfig;
import com.ktb.moyeota.global.outbox.entity.OutboxEvent;
import com.ktb.moyeota.global.outbox.entity.OutboxStatus;
import com.ktb.moyeota.global.outbox.repository.OutboxEventRepository;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.function.BooleanSupplier;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.config.KafkaListenerEndpointRegistry;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.MessageListenerContainer;
import org.springframework.test.annotation.DirtiesContext;
import org.testcontainers.kafka.KafkaContainer;
import tools.jackson.databind.json.JsonMapper;

@SpringBootTest(properties = {
        "moyeota.taxi-pot.matching.mode=kafka",
        "moyeota.taxi-pot.matching.sweep-delay=1s",
        "moyeota.outbox.publish-delay=200ms",
        "spring.datasource.url=jdbc:h2:mem:matching-duplicate-e2e;DB_CLOSE_DELAY=-1",
        "spring.kafka.consumer.auto-offset-reset=earliest",
        "spring.kafka.streams.state-dir=build/tmp/matching-duplicate-e2e"})
@Import({KafkaTestContainerConfig.class, MatchingTopicsConfig.class})
@DirtiesContext
class MatchingDuplicateE2ETest {

    private static final Duration TIMEOUT = Duration.ofSeconds(60);
    private static final Duration SETTLE = Duration.ofSeconds(4);
    private static final Route PANGYO_TO_GANGNAM = new Route(
            "판교역", "37.394726", "127.111209", "강남역", "37.498086", "127.028001");
    private static final Route GANGNAM_TO_JAMSIL = new Route(
            "강남역", "37.498086", "127.028001", "잠실역", "37.513261", "127.100133");
    private static final Route JAMSIL_TO_SEOUL = new Route(
            "잠실역", "37.513261", "127.100133", "서울역", "37.554648", "126.970702");

    @Autowired
    private TaxiPotService taxiPotService;

    @Autowired
    private TaxiPotMatchRequestRepository taxiPotMatchRequestRepository;

    @Autowired
    private TaxiPotRepository taxiPotRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private OutboxEventRepository outboxEventRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private KafkaTemplate<String, String> kafkaTemplate;

    @Autowired
    private KafkaListenerEndpointRegistry listenerRegistry;

    @Autowired
    private KafkaContainer kafka;

    @Autowired
    private JsonMapper jsonMapper;

    private MatchingTopicWatcher watcher;
    private LocalDateTime departureAt;

    @BeforeEach
    void watchTopics() {
        watcher = new MatchingTopicWatcher(kafka.getBootstrapServers(), jsonMapper);
        departureAt = LocalDateTime.now().plusMinutes(30).truncatedTo(ChronoUnit.MINUTES);
    }

    @AfterEach
    void stopWatching() {
        watcher.close();
        decisionConsumer().start();
    }

    @Test
    @DisplayName("취소한 요청이 발행기 재전송으로 다시 와도 다른 사람과 묶이지 않는다")
    void republishedRequestAfterCancelIsNotMatched() {
        User a = user("재전송 A");
        long requestA = request(a, PANGYO_TO_GANGNAM);
        waitUntilPublished();
        pause(SETTLE);
        taxiPotService.cancelMatching(a.getId());
        waitUntilPublished();
        pause(SETTLE);

        republish(MatchingMessages.REQUESTED, requestA);
        waitUntilPublished();
        pause(SETTLE);
        long requestB = request(user("재전송 B"), PANGYO_TO_GANGNAM);
        long requestC = request(user("재전송 C"), PANGYO_TO_GANGNAM);
        waitUntil(() -> matched(requestB) && matched(requestC), "B와 C가 매칭된다");
        pause(SETTLE);

        assertThat(watcher.decisionsWith(requestA)).isEmpty();
        assertThat(watcher.rejectionEventIdsWith(requestB)).isEmpty();
        assertThat(status(requestA)).isEqualTo(TaxiPotMatchRequestStatus.CANCELED);
        assertThat(companionOf(requestB)).isEqualTo(companionOf(requestC));
    }

    @Test
    @DisplayName("같은 결정을 결정 반영이 다시 읽어도 택시팟은 하나다")
    void redeliveredDecisionKeepsOnePot() {
        long requestA = request(user("결정 A"), GANGNAM_TO_JAMSIL);
        long requestB = request(user("결정 B"), GANGNAM_TO_JAMSIL);
        waitUntil(() -> matched(requestA) && matched(requestB), "A와 B가 매칭된다");
        ConsumerRecord<String, String> decision = watcher.decisionsWith(requestA).getFirst();

        kafkaTemplate.send(MatchingMessages.DECISION_TOPIC, decision.key(), decision.value()).join();
        waitUntil(() -> MatchingTopicWatcher.consumedToEnd(kafka.getBootstrapServers(), MatchDecisionConsumer.GROUP_ID),
                "결정 반영이 결정 토픽 끝까지 읽는다");

        long taxiPotId = companionOf(requestA);
        assertThat(companionOf(requestB)).isEqualTo(taxiPotId);
        assertThat(taxiPotRepository.findById(taxiPotId)).get()
                .extracting(Companion::getCurrentCount).isEqualTo(2);
        assertThat(watcher.rejectionEventIdsWith(requestA)).isEmpty();
    }

    @Test
    @DisplayName("같은 거절이 다시 와도 그 뒤에 들어간 새 팟을 깨지 않는다")
    void duplicatedRejectionDoesNotBreakNewPot() {
        decisionConsumer().stop();
        User a = user("거절 A");
        long requestA = request(a, JAMSIL_TO_SEOUL);
        long requestB = request(user("거절 B"), JAMSIL_TO_SEOUL);
        waitUntil(() -> !watcher.decisionsWith(requestB).isEmpty(), "A와 B를 묶은 결정이 나온다");
        taxiPotService.cancelMatching(a.getId());
        waitUntilPublished();
        pause(SETTLE);

        decisionConsumer().start();
        waitUntil(() -> !watcher.rejectionsWith(requestB).isEmpty(), "A가 취소돼 그 결정이 거절된다");
        ConsumerRecord<String, String> rejection = watcher.rejectionsWith(requestB).getFirst();
        long requestC = request(user("거절 C"), JAMSIL_TO_SEOUL);
        waitUntil(() -> matched(requestB) && matched(requestC), "B와 C가 새 팟으로 매칭된다");

        kafkaTemplate.send(MatchingMessages.REQUEST_TOPIC, rejection.key(), rejection.value()).join();
        pause(SETTLE);
        long requestD = request(user("거절 D"), JAMSIL_TO_SEOUL);
        pause(SETTLE.multipliedBy(2));

        assertThat(watcher.decisionsWith(requestB)).hasSize(2);
        assertThat(watcher.rejectionEventIdsWith(requestB)).hasSize(1);
        waitUntil(() -> matched(requestD), "D가 B와 C의 팟에 합류한다");
        assertThat(companionOf(requestD)).isEqualTo(companionOf(requestB)).isEqualTo(companionOf(requestC));
    }

    private User user(String nickname) {
        return userRepository.save(bankAccountHolder(nickname));
    }

    private long request(User user, Route route) {
        taxiPotService.start(user.getId(), new TaxiPotStartCommand(
                route.originName(), new BigDecimal(route.originLat()), new BigDecimal(route.originLng()),
                route.destName(), new BigDecimal(route.destLat()), new BigDecimal(route.destLng()), departureAt));
        return taxiPotMatchRequestRepository.findMatchingIdByUserId(user.getId()).orElseThrow();
    }

    private void republish(String eventType, long requestId) {
        List<Long> ids = outboxEventRepository.findAll().stream()
                .filter(event -> event.getEventType().equals(eventType))
                .filter(event -> jsonMapper.readTree(event.getPayload()).path("request_id").asLong() == requestId)
                .map(OutboxEvent::getId)
                .toList();
        assertThat(ids).hasSize(1);
        jdbcTemplate.update("update outbox_events set status = ? where id = ?", OutboxStatus.PENDING.name(),
                ids.getFirst());
    }

    private void waitUntilPublished() {
        waitUntil(() -> outboxEventRepository.findAll().stream()
                .allMatch(event -> event.getStatus() == OutboxStatus.PUBLISHED), "아웃박스가 모두 발행된다");
    }

    private MessageListenerContainer decisionConsumer() {
        return listenerRegistry.getAllListenerContainers().stream()
                .filter(container -> MatchDecisionConsumer.GROUP_ID.equals(container.getGroupId()))
                .findFirst()
                .orElseThrow();
    }

    private boolean matched(long requestId) {
        return status(requestId) == TaxiPotMatchRequestStatus.MATCHED;
    }

    private TaxiPotMatchRequestStatus status(long requestId) {
        return taxiPotMatchRequestRepository.findById(requestId).orElseThrow().getStatus();
    }

    private long companionOf(long requestId) {
        return taxiPotMatchRequestRepository.findCompanionIdById(requestId).orElseThrow();
    }

    private static void waitUntil(BooleanSupplier condition, String description) {
        long deadline = System.nanoTime() + TIMEOUT.toNanos();
        while (!condition.getAsBoolean()) {
            if (System.nanoTime() > deadline) {
                throw new AssertionError("시간 안에 이루어지지 않음: " + description);
            }
            pause(Duration.ofMillis(200));
        }
    }

    private static void pause(Duration duration) {
        try {
            Thread.sleep(duration.toMillis());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }

    private record Route(String originName, String originLat, String originLng,
                         String destName, String destLat, String destLng) {
    }
}
