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
import java.time.OffsetDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.PropertyNamingStrategies;
import tools.jackson.databind.json.JsonMapper;

class CellEventsTest {

    private static final String POT_ID = "101,102";

    private final JsonMapper jsonMapper = JsonMapper.builder()
            .propertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE)
            .build();
    private final CellEvents events = new CellEvents(jsonMapper);
    private final MapCellEntries entries = new MapCellEntries();

    @BeforeEach
    void potOf101And102With104Waiting() {
        entries.put(CellRequest.waiting(request(101)).joining(POT_ID));
        entries.put(CellRequest.waiting(request(102)).joining(POT_ID));
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

        assertThat(entries.requests).doesNotContainKey(101L);
        assertThat(entries.pots.get(POT_ID).potKey()).isEqualTo("102");
    }

    @Test
    @DisplayName("운행을 시작하면 그 팟과 팟에 든 요청을 지운다")
    void startRideDropsPot() {
        apply(MatchingMessages.RIDE_STARTED, new MatchRideStartedData(List.of(101L)));

        assertThat(entries.pots).isEmpty();
        assertThat(entries.requests).containsOnlyKeys(104L);
    }

    @Test
    @DisplayName("합류할 수 없는 팟이라 거절되면 팟을 지우고 합류 요청만 대기로 돌린다")
    void unjoinablePotRejectionDropsPot() {
        entries.put(entries.requests.get(104L).joining(POT_ID));
        entries.put(new CellPot(POT_ID, DEPARTURE_AT, List.of(101L, 102L, 104L)));

        apply(MatchingMessages.REJECTED, new MatchRejectedData(
                "101,102:104", "101,102", List.of(104L), RejectReason.POT_FULL));

        assertThat(entries.pots).isEmpty();
        assertThat(waitingIds()).containsExactly(104L);
        assertThat(entries.requests).containsOnlyKeys(104L);
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
    @DisplayName("모두 나가면 팟을 지우고, 대기 요청까지 취소되면 칸이 빈다")
    void leaveAllEmptiesCell() {
        apply(MatchingMessages.LEFT, new MatchLeftData(101));
        apply(MatchingMessages.LEFT, new MatchLeftData(102));
        apply(MatchingMessages.CANCELED, new MatchCanceledData(104));

        assertThat(entries.isEmpty()).isTrue();
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
                OffsetDateTime.now(), jsonMapper.valueToTree(data));
    }

    private List<Long> waitingIds() {
        return entries.requests.values().stream()
                .filter(request -> !request.inPot())
                .map(CellRequest::requestId)
                .toList();
    }

    private static MatchRequestData request(long requestId) {
        return MatchRequestData.from(matchRequest(requestId));
    }
}
