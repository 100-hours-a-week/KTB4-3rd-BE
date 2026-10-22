package com.ktb.moyeota.domain.taxipot.matching;

import static com.ktb.moyeota.fixture.MatchRequestFixture.DEPARTURE_AT;
import static com.ktb.moyeota.fixture.MatchRequestFixture.matchRequest;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class GreedySelectorTest {

    private final GreedySelector selector = new GreedySelector();

    @Test
    @DisplayName("앞 순위 후보부터 고르고 이미 쓴 요청이 섞인 후보는 건너뛴다")
    void picksInRankSkippingTakenRequests() {
        List<MatchResult> results = selector.select(List.of(
                newPot(10, 1, 2, 3), newPot(10, 2, 3, 4), newPot(5, 1, 2), newPot(5, 3, 4)));

        assertThat(results).extracting(MatchResult::resultId).containsExactly("1,2,3");
    }

    @Test
    @DisplayName("한 팟에는 한 번만 합류시킨다")
    void joinsEachPotOnce() {
        OpenPot pot = new OpenPot("9", DEPARTURE_AT, List.of(matchRequest(9)));

        List<MatchResult> results = selector.select(List.of(join(pot, 1), join(pot, 2)));

        assertThat(results).extracting(MatchResult::resultId).containsExactly("9:1");
    }

    private MatchCandidate newPot(double spreadMeters, long... requestIds) {
        List<MatchRequest> requests = Arrays.stream(requestIds).mapToObj(id -> matchRequest(id)).toList();
        return new MatchCandidate(MatchResult.openPot(requests, requestIds[0], requestIds[0], DEPARTURE_AT),
                requests.size(), spreadMeters);
    }

    private MatchCandidate join(OpenPot pot, long requestId) {
        return new MatchCandidate(MatchResult.joinPot(pot, List.of(matchRequest(requestId))), 2, 0);
    }
}
