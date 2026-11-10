package com.ktb.moyeota.domain.taxipot.matching;

import static com.ktb.moyeota.fixture.MatchRequestFixture.DEPARTURE_AT;
import static com.ktb.moyeota.fixture.MatchRequestFixture.matchRequest;
import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class MatchResultTest {

    @Test
    @DisplayName("새 팟의 팟 키와 결과 번호는 팟을 연 요청 번호이고 요청 순서와 관계없다")
    void openPotKeyIgnoresRequestOrder() {
        MatchResult forward = MatchResult.openPot(
                List.of(matchRequest(101), matchRequest(105), matchRequest(108)), 105, 101, DEPARTURE_AT);
        MatchResult backward = MatchResult.openPot(
                List.of(matchRequest(108), matchRequest(101), matchRequest(105)), 105, 101, DEPARTURE_AT);

        assertThat(forward.potKey()).isEqualTo("101,105,108");
        assertThat(forward.resultId()).isEqualTo("101,105,108");
        assertThat(backward.resultId()).isEqualTo(forward.resultId());
    }

    @Test
    @DisplayName("요청 번호는 숫자 크기 순으로 정렬한다")
    void sortsRequestIdsNumerically() {
        MatchResult result = MatchResult.openPot(
                List.of(matchRequest(10), matchRequest(9)), 9, 9, DEPARTURE_AT);

        assertThat(result.potKey()).isEqualTo("9,10");
    }

    @Test
    @DisplayName("새 팟 결과는 팟을 열고 기준 요청을 담는다")
    void openPotKeepsAnchors() {
        MatchResult result = MatchResult.openPot(
                List.of(matchRequest(101), matchRequest(105)), 105, 101, DEPARTURE_AT);

        assertThat(result.opensPot()).isTrue();
        assertThat(result.originAnchorRequestId()).isEqualTo(105L);
        assertThat(result.destAnchorRequestId()).isEqualTo(101L);
        assertThat(result.departureAt()).isEqualTo(DEPARTURE_AT);
    }

    @Test
    @DisplayName("합류 결과는 기존 팟 키를 그대로 쓰고 결과 번호에 새로 묶인 요청 번호를 붙인다")
    void joinPotKeepsPotKey() {
        LocalDateTime potDepartureAt = DEPARTURE_AT.plusMinutes(30);
        OpenPot pot = new OpenPot("101", potDepartureAt, List.of(matchRequest(101)));

        MatchResult result = MatchResult.joinPot(pot, List.of(matchRequest(108), matchRequest(105)));

        assertThat(result.potKey()).isEqualTo("101");
        assertThat(result.resultId()).isEqualTo("101:105,108");
        assertThat(result.opensPot()).isFalse();
        assertThat(result.originAnchorRequestId()).isNull();
        assertThat(result.destAnchorRequestId()).isNull();
        assertThat(result.departureAt()).isEqualTo(potDepartureAt);
    }

    @Test
    @DisplayName("팟을 연 결과로 만든 팟에 합류하면 같은 팟 키로 이어진다")
    void joinsPotOpenedByEarlierResult() {
        MatchResult opened = MatchResult.openPot(List.of(matchRequest(101)), 101, 101, DEPARTURE_AT);
        OpenPot pot = new OpenPot(opened.potKey(), opened.departureAt(), opened.requests());

        MatchResult joined = MatchResult.joinPot(pot, List.of(matchRequest(105)));

        assertThat(joined.potKey()).isEqualTo(opened.potKey());
        assertThat(joined.resultId()).isNotEqualTo(opened.resultId());
    }

    @Test
    @DisplayName("만든 뒤 원래 목록이 바뀌어도 결과의 요청은 그대로다")
    void copiesRequests() {
        List<MatchRequest> requests = new ArrayList<>(List.of(matchRequest(101)));
        MatchResult result = MatchResult.openPot(requests, 101, 101, DEPARTURE_AT);

        requests.add(matchRequest(105));

        assertThat(result.requests()).extracting(MatchRequest::requestId).containsExactly(101L);
    }
}
