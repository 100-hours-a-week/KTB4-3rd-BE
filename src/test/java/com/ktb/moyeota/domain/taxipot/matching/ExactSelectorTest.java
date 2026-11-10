package com.ktb.moyeota.domain.taxipot.matching;

import static com.ktb.moyeota.fixture.MatchRequestFixture.DEPARTURE_AT;
import static com.ktb.moyeota.fixture.MatchRequestFixture.matchRequest;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.LongStream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ExactSelectorTest {

    private final ExactSelector selector = new ExactSelector();

    @Test
    @DisplayName("3명 팟 하나보다 2명 팟 둘이 더 많이 태우면 2명 팟 둘을 고른다")
    void maximizesMatchedRequests() {
        List<MatchResult> results = selector.select(List.of(
                newPot(10, 1, 2, 3), newPot(10, 2, 3, 4), newPot(5, 1, 2), newPot(5, 3, 4), newPot(5, 2, 3)));

        assertThat(results).extracting(MatchResult::resultId).containsExactlyInAnyOrder("1,2", "3,4");
    }

    @Test
    @DisplayName("태우는 인원이 같으면 거리 합이 작은 조합을 고른다")
    void breaksTiesBySpread() {
        List<MatchResult> results = selector.select(List.of(
                newPot(30, 1, 2), newPot(30, 3, 4), newPot(5, 1, 3), newPot(5, 2, 4)));

        assertThat(results).extracting(MatchResult::resultId).containsExactlyInAnyOrder("1,3", "2,4");
    }

    @Test
    @DisplayName("겹치지 않는 후보 묶음은 따로 풀어도 모두 고른다")
    void solvesIndependentComponents() {
        List<MatchResult> results = selector.select(List.of(newPot(5, 1, 2), newPot(5, 3, 4), newPot(5, 5, 6)));

        assertThat(results).extracting(MatchResult::resultId).containsExactlyInAnyOrder("1,2", "3,4", "5,6");
    }

    @Test
    @DisplayName("한 팟에는 한 번만 합류시킨다")
    void joinsEachPotOnce() {
        OpenPot pot = new OpenPot("9", DEPARTURE_AT, List.of(matchRequest(9)));
        MatchCandidate first = new MatchCandidate(MatchResult.joinPot(pot, List.of(matchRequest(1))), 2, 0);
        MatchCandidate second = new MatchCandidate(MatchResult.joinPot(pot, List.of(matchRequest(2))), 2, 0);

        assertThat(selector.select(List.of(first, second))).hasSize(1);
    }

    @Test
    @DisplayName("연결된 요청이 한도를 넘으면 그 묶음은 앞 순위부터 고른다")
    void fallsBackToRankOrderWhenComponentTooLarge() {
        List<MatchCandidate> chain = new ArrayList<>();
        chain.add(newPot(10, 1, 2, 3));
        chain.add(newPot(10, 2, 3, 4));
        chain.add(newPot(5, 1, 2));
        chain.add(newPot(5, 3, 4));
        ExactSelector smallLimit = new ExactSelector(3);

        List<MatchResult> results = smallLimit.select(chain);

        assertThat(results).extracting(MatchResult::resultId).containsExactly("1,2,3");
    }

    @Test
    @DisplayName("결과는 후보 순위 순서로 돌려준다")
    void returnsInRankOrder() {
        List<MatchCandidate> ranked = LongStream.rangeClosed(1, 3)
                .mapToObj(i -> newPot(5, 2 * i - 1, 2 * i))
                .toList();

        assertThat(selector.select(ranked)).extracting(MatchResult::resultId).containsExactly("1,2", "3,4", "5,6");
    }

    private MatchCandidate newPot(double spreadMeters, long... requestIds) {
        List<MatchRequest> requests = Arrays.stream(requestIds).mapToObj(id -> matchRequest(id)).toList();
        return new MatchCandidate(MatchResult.openPot(requests, requestIds[0], requestIds[0], DEPARTURE_AT),
                requests.size(), spreadMeters);
    }
}
