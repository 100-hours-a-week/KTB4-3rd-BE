package com.ktb.moyeota.domain.taxipot.matching;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.LongStream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class MatchingConditionTest {

    private static final long ORIGIN_LAT = 37_394_726;
    private static final long ORIGIN_LNG = 127_111_209;
    private static final long DEST_LAT = 37_498_086;
    private static final long DEST_LNG = 127_028_001;
    private static final long JUST_INSIDE_50M = 449;
    private static final long JUST_OUTSIDE_50M = 450;
    private static final long JUST_INSIDE_50M_EAST = 565;
    private static final long JUST_OUTSIDE_50M_EAST = 566;
    private static final LocalDateTime DEPARTURE_AT = LocalDateTime.of(2026, 10, 20, 9, 0);

    private final MatchingCondition condition = new MatchingCondition(new MatchingRules(50, 4));

    @Test
    @DisplayName("출발지와 도착지가 같으면 묶을 수 있고 번호가 작은 요청이 기준이 된다")
    void groupsSamePlaces() {
        GroupFit fit = condition.evaluate(List.of(request(2, 0, 0), request(1, 0, 0))).orElseThrow();

        assertThat(fit.origin()).isEqualTo(new GroupFit.Anchor(1, 0));
        assertThat(fit.dest()).isEqualTo(new GroupFit.Anchor(1, 0));
    }

    @Test
    @DisplayName("출발지가 50m 안이면 묶고 50m를 넘으면 묶지 않는다")
    void originRadiusBoundary() {
        assertThat(condition.evaluate(List.of(request(1, 0, 0), request(2, JUST_INSIDE_50M, 0)))).isPresent();
        assertThat(condition.evaluate(List.of(request(1, 0, 0), request(2, JUST_OUTSIDE_50M, 0)))).isEmpty();
    }

    @Test
    @DisplayName("동서 방향으로도 출발지가 50m 안이면 묶고 50m를 넘으면 묶지 않는다")
    void originRadiusBoundaryEastWest() {
        assertThat(condition.evaluate(List.of(request(1, 0, 0), eastOfOrigin(2, JUST_INSIDE_50M_EAST)))).isPresent();
        assertThat(condition.evaluate(List.of(request(1, 0, 0), eastOfOrigin(2, JUST_OUTSIDE_50M_EAST)))).isEmpty();
    }

    @Test
    @DisplayName("도착지가 50m 안이면 묶고 50m를 넘으면 묶지 않는다")
    void destRadiusBoundary() {
        assertThat(condition.evaluate(List.of(request(1, 0, 0), request(2, 0, JUST_INSIDE_50M)))).isPresent();
        assertThat(condition.evaluate(List.of(request(1, 0, 0), request(2, 0, JUST_OUTSIDE_50M)))).isEmpty();
    }

    @Test
    @DisplayName("서로 89m 떨어진 A와 B도 둘 다 44m 안인 C가 있으면 셋이 묶인다")
    void groupsThroughMiddleRequest() {
        MatchRequest a = request(1, 0, 0);
        MatchRequest b = request(2, 800, 0);
        MatchRequest c = request(3, 400, 0);

        assertThat(condition.evaluate(List.of(a, b))).isEmpty();
        GroupFit fit = condition.evaluate(List.of(a, b, c)).orElseThrow();
        assertThat(fit.origin().requestId()).isEqualTo(3);
    }

    @Test
    @DisplayName("출발지 기준과 도착지 기준은 다른 사람이어도 된다")
    void originAndDestAnchorsMayDiffer() {
        MatchRequest a = request(1, 0, 400);
        MatchRequest b = request(2, 400, 0);
        MatchRequest c = request(3, 800, 800);

        GroupFit fit = condition.evaluate(List.of(a, b, c)).orElseThrow();

        assertThat(fit.origin().requestId()).isEqualTo(2);
        assertThat(fit.dest().requestId()).isEqualTo(1);
    }

    @Test
    @DisplayName("기준이 될 수 있는 사람이 여럿이면 가장 먼 사람과의 거리가 짧은 사람을 고른다")
    void picksAnchorWithSmallestSpread() {
        GroupFit fit = condition.evaluate(List.of(request(1, 0, 0), request(2, 100, 0), request(3, 200, 0)))
                .orElseThrow();

        assertThat(fit.origin().requestId()).isEqualTo(2);
        assertThat(fit.origin().spreadMeters()).isCloseTo(11.12, within(0.01));
    }

    @Test
    @DisplayName("출발 시각이 30분 다르면 묶지 않는다")
    void rejectsDifferentDeparture() {
        MatchRequest early = request(1, 0, 0);
        MatchRequest late = request(2, 0, 0, DEPARTURE_AT.plusMinutes(30));

        assertThat(condition.evaluate(List.of(early, late))).isEmpty();
    }

    @Test
    @DisplayName("정원 4명까지만 묶는다")
    void respectsCapacity() {
        assertThat(condition.evaluate(sameRequests(4))).isPresent();
        assertThat(condition.evaluate(sameRequests(5))).isEmpty();
    }

    @Test
    @DisplayName("요청이 없으면 묶지 않는다")
    void rejectsEmpty() {
        assertThat(condition.evaluate(List.of())).isEmpty();
    }

    @Test
    @DisplayName("반경 0m는 좌표가 완전히 같은 요청만 묶는다")
    void zeroRadiusMeansExactMatch() {
        MatchingCondition exact = new MatchingCondition(new MatchingRules(0, 4));

        assertThat(exact.evaluate(List.of(request(1, 0, 0), request(2, 0, 0)))).isPresent();
        assertThat(exact.evaluate(List.of(request(1, 0, 0), request(2, 1, 0)))).isEmpty();
    }

    private List<MatchRequest> sameRequests(int count) {
        return LongStream.rangeClosed(1, count).mapToObj(id -> request(id, 0, 0)).toList();
    }

    private MatchRequest request(long requestId, long originLatOffset, long destLatOffset) {
        return request(requestId, originLatOffset, destLatOffset, DEPARTURE_AT);
    }

    private MatchRequest request(long requestId, long originLatOffset, long destLatOffset, LocalDateTime departureAt) {
        return request(requestId, new GeoPoint(ORIGIN_LAT + originLatOffset, ORIGIN_LNG),
                new GeoPoint(DEST_LAT + destLatOffset, DEST_LNG), departureAt);
    }

    private MatchRequest eastOfOrigin(long requestId, long originLngOffset) {
        return request(requestId, new GeoPoint(ORIGIN_LAT, ORIGIN_LNG + originLngOffset),
                new GeoPoint(DEST_LAT, DEST_LNG), DEPARTURE_AT);
    }

    private MatchRequest request(long requestId, GeoPoint origin, GeoPoint dest, LocalDateTime departureAt) {
        return new MatchRequest(requestId, requestId, new Place("출발지", origin), new Place("도착지", dest),
                departureAt, departureAt.minusMinutes(30));
    }
}
