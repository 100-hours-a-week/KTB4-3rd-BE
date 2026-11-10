package com.ktb.moyeota.domain.taxipot.message;

import static com.ktb.moyeota.fixture.MatchRequestFixture.DEPARTURE_AT;
import static com.ktb.moyeota.fixture.MatchRequestFixture.matchRequest;
import static org.assertj.core.api.Assertions.assertThat;

import com.ktb.moyeota.domain.taxipot.matching.GeoPoint;
import com.ktb.moyeota.domain.taxipot.matching.MatchRequest;
import com.ktb.moyeota.domain.taxipot.matching.MatchResult;
import com.ktb.moyeota.domain.taxipot.matching.OpenPot;
import com.ktb.moyeota.domain.taxipot.matching.Place;
import com.ktb.moyeota.domain.taxipot.matching.RegionCell;
import com.ktb.moyeota.domain.taxipot.model.MatchApplyOutcome.RejectReason;
import com.ktb.moyeota.global.event.EventEnvelope;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.jackson.autoconfigure.JacksonAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

@SpringBootTest(classes = MatchingMessagesJsonTest.class)
@ImportAutoConfiguration(JacksonAutoConfiguration.class)
class MatchingMessagesJsonTest {

    @Autowired
    private JsonMapper jsonMapper;

    @Test
    @DisplayName("키는 출발지 지역 칸의 행:열 이다")
    void keyIsRegionCell() {
        assertThat(MatchingMessages.key(new RegionCell(2079, 5606))).isEqualTo("2079:5606");
    }

    @Test
    @DisplayName("요청 메시지를 매칭 요청으로 읽는다")
    void readsRequested() {
        String json = """
                {"event_id":"0e9f6c1a-3b7d-4f7e-9a51-6f2c8d1e4b90","event_type":"matching.requested",
                 "schema_version":1,"occurred_at":"2026-10-28T09:12:03.120+09:00",
                 "data":{"request_id":101,"user_id":7,
                         "origin":{"name":"판교역 1번 출구","lat":37.394776,"lng":127.111158},
                         "dest":{"name":"카카오 판교아지트","lat":37.395590,"lng":127.110540},
                         "departure_at":"2026-10-28T09:30:00","requested_at":"2026-10-28T09:12:03.120"}}
                """;

        EventEnvelope<MatchRequestData> envelope = jsonMapper.readValue(json, new TypeReference<>() {
        });

        assertThat(envelope.eventType()).isEqualTo(MatchingMessages.REQUESTED);
        assertThat(envelope.data().toMatchRequest()).isEqualTo(new MatchRequest(101, 7,
                new Place("판교역 1번 출구", new GeoPoint(37_394_776, 127_111_158)),
                new Place("카카오 판교아지트", new GeoPoint(37_395_590, 127_110_540)),
                LocalDateTime.of(2026, 10, 28, 9, 30), LocalDateTime.of(2026, 10, 28, 9, 12, 3, 120_000_000)));
    }

    @Test
    @DisplayName("새 팟 결정은 JSON 을 거쳐도 같은 매칭 결과로 돌아온다")
    void decidedOpenPotRoundTrips() {
        MatchResult result = MatchResult.openPot(
                List.of(matchRequest(101), matchRequest(102), matchRequest(104)), 101, 102, DEPARTURE_AT);

        MatchDecidedData read = roundTrip(MatchDecidedData.from(result));

        assertThat(read.toMatchResult()).isEqualTo(result);
    }

    @Test
    @DisplayName("합류 결정은 기준 요청 없이 팟 키와 합류 요청만 담는다")
    void decidedJoinRoundTrips() {
        OpenPot pot = new OpenPot("101,102,104", DEPARTURE_AT,
                List.of(matchRequest(101), matchRequest(102), matchRequest(104)));
        MatchResult result = MatchResult.joinPot(pot, List.of(matchRequest(107)));

        JsonNode json = jsonMapper.valueToTree(MatchDecidedData.from(result));

        assertThat(json.get("result_id").asString()).isEqualTo("101,102,104:107");
        assertThat(json.get("pot_key").asString()).isEqualTo("101,102,104");
        assertThat(json.get("origin_anchor_request_id").isNull()).isTrue();
        assertThat(json.get("requests")).hasSize(1);
        assertThat(roundTrip(MatchDecidedData.from(result)).toMatchResult()).isEqualTo(result);
    }

    @Test
    @DisplayName("좌표는 소수 6자리 도로 나간다")
    void writesDegrees() {
        JsonNode json = jsonMapper.valueToTree(MatchRequestData.from(matchRequest(101)));

        assertThat(json.get("origin").get("lat").decimalValue()).isEqualByComparingTo(new BigDecimal("37.394726"));
        assertThat(json.get("departure_at").asString()).isEqualTo("2026-10-20T09:00:00");
    }

    @Test
    @DisplayName("반영 거절 메시지는 거절 이유를 이름으로 담는다")
    void readsRejected() {
        String json = """
                {"result_id":"101,102,104","pot_key":"101,102,104","request_ids":[101,102,104],
                 "reason":"REQUEST_NOT_MATCHING"}
                """;

        MatchRejectedData rejected = jsonMapper.readValue(json, MatchRejectedData.class);

        assertThat(rejected).isEqualTo(new MatchRejectedData(
                "101,102,104", "101,102,104", List.of(101L, 102L, 104L), RejectReason.REQUEST_NOT_MATCHING));
    }

    private MatchDecidedData roundTrip(MatchDecidedData data) {
        return jsonMapper.readValue(jsonMapper.writeValueAsString(data), MatchDecidedData.class);
    }
}
