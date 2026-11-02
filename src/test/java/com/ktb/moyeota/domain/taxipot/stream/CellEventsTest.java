package com.ktb.moyeota.domain.taxipot.stream;

import static com.ktb.moyeota.fixture.MatchRequestFixture.DEPARTURE_AT;
import static com.ktb.moyeota.fixture.MatchRequestFixture.matchRequest;
import static org.assertj.core.api.Assertions.assertThat;

import com.ktb.moyeota.domain.taxipot.message.MatchCanceledData;
import com.ktb.moyeota.domain.taxipot.message.MatchLeftData;
import com.ktb.moyeota.domain.taxipot.message.MatchRejectedData;
import com.ktb.moyeota.domain.taxipot.message.MatchRequestData;
import com.ktb.moyeota.domain.taxipot.message.MatchRideStartedData;
import com.ktb.moyeota.domain.taxipot.message.MatchingMessages;
import com.ktb.moyeota.domain.taxipot.model.MatchApplyOutcome.RejectReason;
import com.ktb.moyeota.global.event.EventEnvelope;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.PropertyNamingStrategies;
import tools.jackson.databind.json.JsonMapper;

class CellEventsTest {

    private static final String POT_ID = "101,102";
    private static final OffsetDateTime OCCURRED_AT = OffsetDateTime.of(2026, 10, 20, 0, 10, 0, 0, ZoneOffset.UTC);

    private final JsonMapper jsonMapper = JsonMapper.builder()
            .propertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE)
            .build();
    private final CellEvents events = new CellEvents(jsonMapper, ZoneId.of("Asia/Seoul"));
    private final MapCellEntries entries = new MapCellEntries();

    @BeforeEach
    void potOf101And102With104Waiting() {
        entries.put(CellRequest.waiting(request(101)).joining(POT_ID, POT_ID));
        entries.put(CellRequest.waiting(request(102)).joining(POT_ID, POT_ID));
        entries.put(CellRequest.waiting(request(104)));
        entries.put(new CellPot(POT_ID, DEPARTURE_AT, List.of(101L, 102L)));
    }

    @Test
    @DisplayName("같은 요청이 다시 오면 대기에도 팟에도 더 넣지 않는다")
    void ignoresDuplicateRequest() {
        apply(MatchingMessages.REQUESTED, request(104));
        apply(MatchingMessages.REQUESTED, request(101));

        assertThat(waitingIds()).containsExactly(104L);
        assertThat(entries.requests.get(101L).potId()).isEqualTo(POT_ID);
    }

    @Test
    @DisplayName("팟에 든 요청이 취소되면 그 팟 결정은 거절될 것이므로 팟에서도 뺀다")
    void cancelRemovesFromPot() {
        apply(MatchingMessages.CANCELED, new MatchCanceledData(101));

        assertThat(finishedIds()).containsExactly(101L);
        assertThat(entries.pots.get(POT_ID).potKey()).isEqualTo("102");
    }

    @Test
    @DisplayName("취소한 요청이 다시 와도 대기에 넣지 않는다")
    void ignoresRequestAfterCancel() {
        apply(MatchingMessages.CANCELED, new MatchCanceledData(104));
        apply(MatchingMessages.REQUESTED, request(104));

        assertThat(waitingIds()).isEmpty();
        assertThat(finishedIds()).containsExactly(104L);
    }

    @Test
    @DisplayName("취소가 요청보다 먼저 와도 뒤에 온 요청을 대기에 넣지 않는다")
    void ignoresRequestArrivingAfterItsCancel() {
        apply(MatchingMessages.CANCELED, new MatchCanceledData(105));
        apply(MatchingMessages.REQUESTED, request(105));
        apply(MatchingMessages.REQUESTED, request(105));

        assertThat(waitingIds()).containsExactly(104L);
        assertThat(entries.requests.get(105L).finished()).isTrue();
        assertThat(entries.requests.get(105L).request()).isEqualTo(request(105));
    }

    @Test
    @DisplayName("요청보다 먼저 온 취소는 취소 시각을 서울 시각으로 남긴다")
    void cancelBeforeArrivalKeepsCanceledAt() {
        apply(MatchingMessages.CANCELED, new MatchCanceledData(105));

        assertThat(entries.requests.get(105L).canceledAt()).isEqualTo(LocalDateTime.of(2026, 10, 20, 9, 10));
    }

    @Test
    @DisplayName("운행을 시작하면 그 팟을 지우고 팟에 든 요청은 끝난 요청으로 남긴다")
    void startRideDropsPot() {
        apply(MatchingMessages.RIDE_STARTED, new MatchRideStartedData(List.of(101L)));

        assertThat(entries.pots).isEmpty();
        assertThat(finishedIds()).containsExactly(101L, 102L);
        assertThat(waitingIds()).containsExactly(104L);
    }

    @Test
    @DisplayName("운행을 시작한 요청이 다시 와도 대기에 넣지 않는다")
    void ignoresRequestAfterRideStarted() {
        apply(MatchingMessages.RIDE_STARTED, new MatchRideStartedData(List.of(101L)));
        apply(MatchingMessages.REQUESTED, request(101));

        assertThat(waitingIds()).containsExactly(104L);
    }

    @Test
    @DisplayName("합류할 수 없는 팟이라 거절되면 팟을 지우고 합류 요청만 대기로 돌린다")
    void unjoinablePotRejectionDropsPot() {
        entries.put(entries.requests.get(104L).joining(POT_ID, "101,102:104"));
        entries.put(new CellPot(POT_ID, DEPARTURE_AT, List.of(101L, 102L, 104L)));

        apply(MatchingMessages.REJECTED, new MatchRejectedData(
                "101,102:104", "101,102", List.of(104L), RejectReason.POT_FULL));

        assertThat(entries.pots).isEmpty();
        assertThat(waitingIds()).containsExactly(104L);
        assertThat(finishedIds()).containsExactly(101L, 102L);
    }

    @Test
    @DisplayName("새 팟이 거절되면 팟을 지우고 남은 요청을 모두 대기로 돌린다")
    void rejectedPotReturnsMembers() {
        apply(MatchingMessages.REJECTED, new MatchRejectedData(
                POT_ID, POT_ID, List.of(101L, 102L), RejectReason.REQUEST_NOT_MATCHING));

        assertThat(entries.pots).isEmpty();
        assertThat(waitingIds()).containsExactly(101L, 102L, 104L);
    }

    @Test
    @DisplayName("다른 결정에 대한 거절은 지금 팟을 깨지 않는다")
    void ignoresRejectionOfAnotherDecision() {
        apply(MatchingMessages.REJECTED, new MatchRejectedData(
                "101,102,103", "101,102,103", List.of(101L, 102L), RejectReason.REQUEST_NOT_MATCHING));

        assertThat(entries.pots.get(POT_ID).memberIds()).containsExactly(101L, 102L);
        assertThat(waitingIds()).containsExactly(104L);
    }

    @Test
    @DisplayName("거절로 대기에 돌아가 새 팟에 든 뒤 같은 거절이 늦게 다시 와도 새 팟을 깨지 않는다")
    void ignoresLateDuplicateRejection() {
        MatchRejectedData rejected = new MatchRejectedData(
                POT_ID, POT_ID, List.of(101L, 102L), RejectReason.POT_NOT_RECRUITING);
        apply(MatchingMessages.REJECTED, rejected);
        String newPot = "101,102,104";
        for (long requestId : List.of(101L, 102L, 104L)) {
            entries.put(entries.requests.get(requestId).joining(newPot, newPot));
        }
        entries.put(new CellPot(newPot, DEPARTURE_AT, List.of(101L, 102L, 104L)));

        apply(MatchingMessages.REJECTED, rejected);

        assertThat(entries.pots).containsOnlyKeys(newPot);
        assertThat(entries.requests.values()).extracting(CellRequest::potId).containsOnly(newPot);
    }

    @Test
    @DisplayName("모두 나가면 팟을 지우고, 대기 요청까지 취소되면 끝난 요청만 남는다")
    void leaveAllEmptiesCell() {
        apply(MatchingMessages.LEFT, new MatchLeftData(101));
        apply(MatchingMessages.LEFT, new MatchLeftData(102));
        apply(MatchingMessages.CANCELED, new MatchCanceledData(104));

        assertThat(entries.pots).isEmpty();
        assertThat(waitingIds()).isEmpty();
        assertThat(finishedIds()).containsExactly(101L, 102L, 104L);
    }

    @Test
    @DisplayName("모르는 이벤트 종류는 반영하지 않고 알린다")
    void unknownEventType() {
        boolean applied = events.apply(envelope("matching.unknown", new MatchCanceledData(104)), entries);

        assertThat(applied).isFalse();
        assertThat(waitingIds()).containsExactly(104L);
    }

    private void apply(String eventType, Object data) {
        events.apply(envelope(eventType, data), entries);
    }

    private EventEnvelope<JsonNode> envelope(String eventType, Object data) {
        return new EventEnvelope<>("event-" + eventType, eventType, MatchingMessages.SCHEMA_VERSION,
                OCCURRED_AT, jsonMapper.valueToTree(data));
    }

    private List<Long> waitingIds() {
        return entries.requests.values().stream()
                .filter(CellRequest::matchable)
                .map(CellRequest::requestId)
                .toList();
    }

    private List<Long> finishedIds() {
        return entries.requests.values().stream()
                .filter(CellRequest::finished)
                .map(CellRequest::requestId)
                .toList();
    }

    private static MatchRequestData request(long requestId) {
        return MatchRequestData.from(matchRequest(requestId));
    }
}
