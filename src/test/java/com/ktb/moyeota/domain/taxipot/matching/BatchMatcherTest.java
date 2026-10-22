package com.ktb.moyeota.domain.taxipot.matching;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.stream.LongStream;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class BatchMatcherTest {

    private static final long ORIGIN_LAT = 37_394_726;
    private static final long ORIGIN_LNG = 127_111_209;
    private static final GeoPoint DEST = new GeoPoint(37_498_086, 127_028_001);
    private static final LocalDateTime DEPARTURE_AT = LocalDateTime.of(2026, 10, 20, 9, 0);
    private static final int NEIGHBOR_LIMIT = 8;

    private final BatchMatcher matcher = new BatchMatcher(new MatchingRules(50, 4), NEIGHBOR_LIMIT);

    @Test
    @DisplayName("서로 89m인 A와 B는 둘만으로는 묶이지 않고 기다린다")
    void farPairKeepsWaiting() {
        List<MatchResult> results = matcher.match(List.of(request(1, 0), request(2, 800)), List.of());

        assertThat(results).isEmpty();
    }

    @Test
    @DisplayName("A와 B 사이에 C가 오면 셋이 한 팟이 된다")
    void middleRequestJoinsAllThree() {
        List<MatchResult> results = matcher.match(
                List.of(request(1, 0), request(2, 800), request(3, 400)), List.of());

        assertThat(results).singleElement().satisfies(result -> {
            assertThat(result.opensPot()).isTrue();
            assertThat(requestIds(result)).containsExactly(1L, 2L, 3L);
            assertThat(result.originAnchorRequestId()).isEqualTo(3L);
        });
    }

    @Test
    @DisplayName("같은 곳에 5명이 모이면 4명이 한 팟이 되고 1명은 기다린다")
    void fillsUpToCapacity() {
        List<MatchRequest> five = LongStream.rangeClosed(1, 5).mapToObj(id -> request(id, 0)).toList();

        List<MatchResult> results = matcher.match(five, List.of());

        assertThat(results).singleElement()
                .satisfies(result -> assertThat(requestIds(result)).containsExactly(1L, 2L, 3L, 4L));
    }

    @Test
    @DisplayName("인원이 같으면 서로 더 가까운 사람끼리 묶는다")
    void prefersCloserGroupOfSameSize() {
        BatchMatcher pairs = new BatchMatcher(new MatchingRules(50, 2), NEIGHBOR_LIMIT);

        List<MatchResult> results = pairs.match(
                List.of(request(1, 0), request(2, 400), request(3, 100)), List.of());

        assertThat(results).singleElement()
                .satisfies(result -> assertThat(requestIds(result)).containsExactly(1L, 3L));
    }

    @Test
    @DisplayName("자리 남은 팟에 넣어 더 큰 팟을 만들 수 있으면 새 팟보다 먼저 넣는다")
    void prefersJoiningOpenPotForLargerPot() {
        OpenPot pot = openPot(request(10, 0), request(11, 0));

        List<MatchResult> results = matcher.match(List.of(request(1, 0), request(2, 0)), List.of(pot));

        assertThat(results).singleElement().satisfies(result -> {
            assertThat(result.potKey()).isEqualTo(pot.potKey());
            assertThat(requestIds(result)).containsExactly(1L, 2L);
        });
    }

    @Test
    @DisplayName("따로는 들어갈 수 있어도 함께 들어가면 조건이 깨지는 두 요청은 한 명만 넣는다")
    void joinsOnlyOneWhenTogetherTheyBreakTheRule() {
        OpenPot pot = openPot(request(10, 0), request(11, 400));
        MatchRequest north = request(1, 800);
        MatchRequest south = request(2, -400);

        List<MatchResult> results = matcher.match(List.of(north, south), List.of(pot));

        assertThat(results).singleElement().satisfies(result -> {
            assertThat(result.potKey()).isEqualTo(pot.potKey());
            assertThat(result.requests()).hasSize(1);
        });
    }

    @Test
    @DisplayName("요청과 팟이 들어온 순서가 달라도 결과는 같다")
    void sameResultsRegardlessOfInputOrder() {
        List<MatchRequest> requests = List.of(
                request(1, 0), request(2, 100), request(3, 400), request(4, 800), request(5, 1_200), request(6, 1_300));
        List<OpenPot> pots = List.of(openPot(request(10, 1_250)), openPot(request(20, 50)));

        List<MatchResult> forward = matcher.match(requests, pots);
        List<MatchResult> backward = matcher.match(requests.reversed(), pots.reversed());

        assertThat(backward).containsExactlyElementsOf(forward);
    }

    @Test
    @DisplayName("똑같이 좋은 팟이 둘이면 팟이 들어온 순서와 관계없이 같은 팟을 고른다")
    void breaksTieBetweenPotsRegardlessOfOrder() {
        OpenPot first = openPot(request(10, 200));
        OpenPot second = openPot(request(20, 200));
        List<MatchRequest> requests = List.of(request(1, 0));

        List<MatchResult> firstListed = matcher.match(requests, List.of(first, second));
        List<MatchResult> secondListed = matcher.match(requests, List.of(second, first));

        assertThat(firstListed).singleElement()
                .satisfies(result -> assertThat(result.potKey()).isEqualTo(first.potKey()));
        assertThat(secondListed).containsExactlyElementsOf(firstListed);
    }

    @Test
    @DisplayName("좌표가 완전히 같은 요청은 번호 순으로 4명씩 먼저 묶는다")
    void fillsSamePlaceRequestsByRequestIdFirst() {
        List<MatchRequest> nine = LongStream.rangeClosed(9, 17).mapToObj(id -> request(id, 0)).toList();

        List<MatchResult> results = matcher.match(nine, List.of());

        assertThat(results).extracting(this::requestIds)
                .containsExactly(List.of(9L, 10L, 11L, 12L), List.of(13L, 14L, 15L, 16L));
    }

    @Test
    @DisplayName("300명이 100m 안에 몰려도 5초 안에 조건에 맞는 팟으로만 묶는다")
    void handlesCrowdedBatchQuickly() {
        Random random = new Random(1);
        List<MatchRequest> crowd = LongStream.rangeClosed(1, 300)
                .mapToObj(id -> request(id, offset(random), offset(random), offset(random), offset(random)))
                .toList();
        OpenPot pot = openPot(request(1_000, 0));

        List<MatchResult> results = assertTimeoutPreemptively(Duration.ofSeconds(5),
                () -> matcher.match(crowd, List.of(pot)));

        MatchingCondition condition = new MatchingCondition(new MatchingRules(50, 4));
        Set<Long> matched = new HashSet<>();
        for (MatchResult result : results) {
            List<MatchRequest> group = result.opensPot()
                    ? result.requests()
                    : Stream.concat(pot.members().stream(), result.requests().stream()).toList();
            assertThat(condition.evaluate(group)).isPresent();
            result.requests().forEach(request -> assertThat(matched.add(request.requestId())).isTrue());
        }
        assertThat(matched).hasSizeGreaterThan(270);
    }

    private OpenPot openPot(MatchRequest... members) {
        return new OpenPot(MatchResult.openPot(List.of(members), members[0].requestId(), members[0].requestId(),
                DEPARTURE_AT).potKey(), DEPARTURE_AT, List.of(members));
    }

    private List<Long> requestIds(MatchResult result) {
        return result.requests().stream().map(MatchRequest::requestId).sorted().toList();
    }

    private long offset(Random random) {
        return random.nextLong(-300, 301);
    }

    private MatchRequest request(long requestId, long originLatOffset) {
        return request(requestId, originLatOffset, 0, 0, 0);
    }

    private MatchRequest request(
            long requestId, long originLatOffset, long originLngOffset, long destLatOffset, long destLngOffset) {
        Place origin = new Place("출발지", new GeoPoint(ORIGIN_LAT + originLatOffset, ORIGIN_LNG + originLngOffset));
        Place dest = new Place("도착지", new GeoPoint(DEST.latMicros() + destLatOffset, DEST.lngMicros() + destLngOffset));
        return new MatchRequest(requestId, requestId, origin, dest, DEPARTURE_AT, DEPARTURE_AT.minusMinutes(30));
    }
}
