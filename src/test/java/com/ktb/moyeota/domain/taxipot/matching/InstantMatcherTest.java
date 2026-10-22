package com.ktb.moyeota.domain.taxipot.matching;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class InstantMatcherTest {

    private static final long ORIGIN_LAT = 37_394_726;
    private static final long ORIGIN_LNG = 127_111_209;
    private static final GeoPoint DEST = new GeoPoint(37_498_086, 127_028_001);
    private static final LocalDateTime DEPARTURE_AT = LocalDateTime.of(2026, 10, 20, 9, 0);

    private final InstantMatcher matcher = new InstantMatcher(new MatchingRules(50, 4));

    @Test
    @DisplayName("들어갈 팟이 없으면 혼자 새 팟을 연다")
    void opensPotAloneWhenNothingFits() {
        MatchResult result = matcher.match(request(1, 0), List.of(openPot(request(10, 800))));

        assertThat(result.opensPot()).isTrue();
        assertThat(result.requests()).extracting(MatchRequest::requestId).containsExactly(1L);
        assertThat(result.originAnchorRequestId()).isEqualTo(1L);
    }

    @Test
    @DisplayName("50m 안의 팟이 여럿이면 가장 가까운 팟에 들어간다")
    void joinsNearestPot() {
        OpenPot far = openPot(request(10, 400));
        OpenPot near = openPot(request(20, 100));

        MatchResult result = matcher.match(request(1, 0), List.of(far, near));

        assertThat(result.potKey()).isEqualTo(near.potKey());
    }

    @Test
    @DisplayName("정원이 찬 팟은 건너뛰고 새 팟을 연다")
    void skipsFullPot() {
        OpenPot full = openPot(request(10, 0), request(11, 0), request(12, 0), request(13, 0));

        MatchResult result = matcher.match(request(1, 0), List.of(full));

        assertThat(result.opensPot()).isTrue();
    }

    @Test
    @DisplayName("똑같이 가까운 팟이 둘이면 팟이 들어온 순서와 관계없이 같은 팟을 고른다")
    void breaksTieRegardlessOfOrder() {
        OpenPot first = openPot(request(10, 200));
        OpenPot second = openPot(request(20, 200));

        MatchResult firstListed = matcher.match(request(1, 0), List.of(first, second));
        MatchResult secondListed = matcher.match(request(1, 0), List.of(second, first));

        assertThat(firstListed.potKey()).isEqualTo(first.potKey());
        assertThat(secondListed).isEqualTo(firstListed);
    }

    @Test
    @DisplayName("A, B, C 순서로 오면 먼저 열린 두 팟이 합쳐지지 않아 2명과 1명으로 나뉜다")
    void splitsIntoTwoPotsWhenFarPairComesFirst() {
        List<OpenPot> pots = replay(request(1, 0), request(2, 800), request(3, 400));

        assertThat(pots).extracting(pot -> pot.members().size()).containsExactlyInAnyOrder(2, 1);
    }

    @Test
    @DisplayName("반경 0m는 좌표가 완전히 같은 팟에만 들어간다")
    void zeroRadiusJoinsOnlyExactPot() {
        InstantMatcher exact = new InstantMatcher(new MatchingRules(0, 4));
        OpenPot pot = openPot(request(10, 0));

        assertThat(exact.match(request(1, 0), List.of(pot)).potKey()).isEqualTo(pot.potKey());
        assertThat(exact.match(request(2, 1), List.of(pot)).opensPot()).isTrue();
    }

    private List<OpenPot> replay(MatchRequest... requests) {
        List<OpenPot> pots = new ArrayList<>();
        for (MatchRequest request : requests) {
            MatchResult result = matcher.match(request, pots);
            if (result.opensPot()) {
                pots.add(new OpenPot(result.potKey(), result.departureAt(), result.requests()));
                continue;
            }
            OpenPot joined = pots.stream().filter(pot -> pot.potKey().equals(result.potKey())).findFirst().orElseThrow();
            pots.set(pots.indexOf(joined), new OpenPot(joined.potKey(), joined.departureAt(),
                    Stream.concat(joined.members().stream(), result.requests().stream()).toList()));
        }
        return pots;
    }

    private OpenPot openPot(MatchRequest... members) {
        return new OpenPot(MatchResult.openPot(List.of(members), members[0].requestId(), members[0].requestId(),
                DEPARTURE_AT).potKey(), DEPARTURE_AT, List.of(members));
    }

    private MatchRequest request(long requestId, long originLatOffset) {
        Place origin = new Place("출발지", new GeoPoint(ORIGIN_LAT + originLatOffset, ORIGIN_LNG));
        return new MatchRequest(requestId, requestId, origin, new Place("도착지", DEST), DEPARTURE_AT,
                DEPARTURE_AT.minusMinutes(30));
    }
}
