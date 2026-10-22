package com.ktb.moyeota.domain.taxipot.matching.simulation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import com.ktb.moyeota.domain.taxipot.matching.GeoPoint;
import com.ktb.moyeota.domain.taxipot.matching.MatchRequest;
import com.ktb.moyeota.domain.taxipot.matching.Place;
import com.ktb.moyeota.domain.taxipot.matching.RegionGrid;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class MatchingSimulatorTest {

    private static final GeoPoint ORIGIN = new GeoPoint(37_394_726, 127_111_209);
    private static final GeoPoint DEST = new GeoPoint(37_408_500, 127_100_500);
    private static final LocalDateTime EIGHT = LocalDateTime.of(2026, 10, 20, 8, 0);
    private static final LocalDateTime DEPARTURE_AT = EIGHT.plusMinutes(30);
    private static final int RADIUS_METERS = 50;

    private final MatchingSimulator simulator = new MatchingSimulator();

    @Test
    @DisplayName("A, B, C 순서로 오면 즉시 매칭은 2명과 1명으로 나뉘고 묶음 매칭은 3명 한 팟이 된다")
    void batchJoinsFarPairThroughMiddle() {
        List<MatchRequest> flow = List.of(request(1, 0, 1), request(2, 800, 5), request(3, 400, 9));

        SimulationResult instant = simulator.run(flow, MatchingMethod.instant(RADIUS_METERS));
        SimulationResult batch = simulator.run(flow, MatchingMethod.batch(30, 2_000, 8, RADIUS_METERS));

        assertThat(instant.pots()).isEqualTo(1);
        assertThat(instant.averagePotSize()).isEqualTo(2);
        assertThat(instant.aloneRatio()).isCloseTo(1.0 / 3, within(1e-9));
        assertThat(batch.pots()).isEqualTo(1);
        assertThat(batch.averagePotSize()).isEqualTo(3);
        assertThat(batch.aloneRatio()).isZero();
    }

    @Test
    @DisplayName("묶음 매칭은 다음 주기까지 기다린 시간이, 즉시 매칭은 상대가 올 때까지 기다린 시간이 매칭 시간이다")
    void waitIsUntilNextTickOrUntilPartnerArrives() {
        List<MatchRequest> flow = List.of(request(1, 0, 0), request(2, 0, 5));

        SimulationResult instant = simulator.run(flow, MatchingMethod.instant(RADIUS_METERS));
        SimulationResult batch = simulator.run(flow, MatchingMethod.batch(30, 2_000, 8, RADIUS_METERS));

        assertThat(instant.medianWait()).isEqualTo(Duration.ZERO);
        assertThat(instant.p95Wait()).isEqualTo(Duration.ofSeconds(5));
        assertThat(batch.medianWait()).isEqualTo(Duration.ofSeconds(25));
        assertThat(batch.p95Wait()).isEqualTo(Duration.ofSeconds(30));
    }

    @Test
    @DisplayName("한 번 묶인 요청은 다음 주기에 다시 묶이지 않는다")
    void matchedRequestIsNotMatchedAgain() {
        List<MatchRequest> flow = List.of(request(1, 0, 0), request(2, 0, 5), request(3, 5_000, 40));

        SimulationResult batch = simulator.run(flow, MatchingMethod.batch(30, 2_000, 8, RADIUS_METERS));

        assertThat(batch.pots()).isEqualTo(1);
        assertThat(batch.averagePotSize()).isEqualTo(2);
        assertThat(batch.matchedRequests()).isEqualTo(2);
    }

    @Test
    @DisplayName("함께 탈 사람이 오지 않으면 끝까지 혼자 남은 요청으로 센다")
    void requestWithoutPartnerStaysAlone() {
        List<MatchRequest> flow = List.of(request(1, 0, 0));

        assertThat(simulator.run(flow, MatchingMethod.instant(RADIUS_METERS)).aloneRatio()).isEqualTo(1.0);
        assertThat(simulator.run(flow, MatchingMethod.batch(30, 2_000, 8, RADIUS_METERS)).aloneRatio()).isEqualTo(1.0);
    }

    @Test
    @DisplayName("10m 떨어진 두 요청은 완전 일치로는 혼자 남고 50m로는 한 팟이 된다")
    void exactBaselineMissesNearbyPair() {
        List<MatchRequest> flow = List.of(request(1, 0, 0), request(2, 90, 5));

        assertThat(simulator.run(flow, MatchingMethod.exact()).aloneRatio()).isEqualTo(1.0);
        assertThat(simulator.run(flow, MatchingMethod.instant(RADIUS_METERS)).aloneRatio()).isZero();
    }

    @Test
    @DisplayName("60m 떨어진 두 요청은 반경 50m로는 혼자 남고 반경 100m로는 한 팟이 된다")
    void radiusParameterWidensMatching() {
        List<MatchRequest> flow = List.of(request(1, 0, 0), request(2, 540, 5));

        assertThat(simulator.run(flow, MatchingMethod.instant(50)).aloneRatio()).isEqualTo(1.0);
        assertThat(simulator.run(flow, MatchingMethod.instant(100)).aloneRatio()).isZero();
        assertThat(simulator.run(flow, MatchingMethod.batch(10, 2_000, 8, 100)).aloneRatio()).isZero();
    }

    @Test
    @DisplayName("칸 경계 양쪽의 두 요청은 격자가 있으면 혼자 남고 격자가 없으면 한 팟이 된다")
    void gridBoundarySplitsNearbyPair() {
        long boundaryOffset = offsetToNextRow(new RegionGrid(2_000));
        List<MatchRequest> flow = List.of(request(1, boundaryOffset - 5, 0), request(2, boundaryOffset + 5, 5));

        assertThat(simulator.run(flow, MatchingMethod.batch(30, 2_000, 8, RADIUS_METERS)).aloneRatio()).isEqualTo(1.0);
        assertThat(simulator.run(flow, MatchingMethod.batch(30, 0, 8, RADIUS_METERS)).aloneRatio()).isZero();
    }

    private long offsetToNextRow(RegionGrid grid) {
        long offset = 0;
        while (grid.cellOf(shifted(offset)).equals(grid.cellOf(ORIGIN))) {
            offset++;
        }
        return offset;
    }

    private GeoPoint shifted(long latOffset) {
        return new GeoPoint(ORIGIN.latMicros() + latOffset, ORIGIN.lngMicros());
    }

    private MatchRequest request(long requestId, long originLatOffset, long requestedSecond) {
        return new MatchRequest(requestId, requestId, new Place("출발지", shifted(originLatOffset)),
                new Place("도착지", DEST), DEPARTURE_AT, EIGHT.plusSeconds(requestedSecond));
    }
}
