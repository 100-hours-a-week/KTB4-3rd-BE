package com.ktb.moyeota.domain.taxipot.stream;

import static com.ktb.moyeota.fixture.MatchRequestFixture.DEPARTURE_AT;
import static com.ktb.moyeota.fixture.MatchRequestFixture.matchRequest;
import static org.assertj.core.api.Assertions.assertThat;

import com.ktb.moyeota.domain.taxipot.matching.MatchResult;
import com.ktb.moyeota.domain.taxipot.matching.MatchingRules;
import com.ktb.moyeota.domain.taxipot.message.MatchRequestData;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class CellSweepTest {

    private final CellSweep sweep = new CellSweep(new MatchingRules(100, 4), 8);
    private final MapCellEntries entries = new MapCellEntries();

    @Test
    @DisplayName("묶인 요청은 새 팟의 구성원이 되고 팟 번호는 결과 번호다")
    void opensPotFromWaiting() {
        waiting(101, 102);

        List<MatchResult> results = sweep.sweep(entries.view(), DEPARTURE_AT, entries);

        assertThat(results).extracting(MatchResult::resultId).containsExactly("101,102");
        assertThat(entries.pots.get("101,102").memberIds()).containsExactly(101L, 102L);
        assertThat(entries.requests.values()).extracting(CellRequest::potId).containsOnly("101,102");
    }

    @Test
    @DisplayName("열린 팟이 있으면 새 요청을 그 팟에 합류시키고 팟 번호는 그대로 둔다")
    void joinsOpenPot() {
        waiting(101, 102);
        sweep.sweep(entries.view(), DEPARTURE_AT, entries);
        waiting(104);

        List<MatchResult> results = sweep.sweep(entries.view(), DEPARTURE_AT, entries);

        assertThat(results).extracting(MatchResult::resultId).containsExactly("101,102:104");
        assertThat(entries.pots).containsOnlyKeys("101,102");
        assertThat(entries.pots.get("101,102").potKey()).isEqualTo("101,102,104");
        assertThat(entries.requests.get(104L).potId()).isEqualTo("101,102");
    }

    @Test
    @DisplayName("출발 시각이 기준보다 이른 요청과 팟만 지운다")
    void dropsOnlyDeparted() {
        waiting(101, 102);
        sweep.sweep(entries.view(), DEPARTURE_AT, entries);
        waiting(104);

        sweep.sweep(entries.view(), DEPARTURE_AT.plusMinutes(1), entries);

        assertThat(entries.isEmpty()).isTrue();
    }

    @Test
    @DisplayName("바뀐 것이 없으면 아무것도 쓰지 않는다")
    void writesNothingWhenNothingChanges() {
        waiting(101);
        MapCellEntries written = new MapCellEntries();

        List<MatchResult> results = sweep.sweep(entries.view(), DEPARTURE_AT, written);

        assertThat(results).isEmpty();
        assertThat(written.isEmpty()).isTrue();
    }

    private void waiting(long... requestIds) {
        for (long requestId : requestIds) {
            entries.put(CellRequest.waiting(MatchRequestData.from(matchRequest(requestId))));
        }
    }
}
