package com.ktb.moyeota.domain.taxipot.repository;

import static com.ktb.moyeota.fixture.MatchRequestFixture.DEPARTURE_AT;
import static com.ktb.moyeota.fixture.UserFixture.bankAccountHolder;
import static org.assertj.core.api.Assertions.assertThat;

import com.ktb.moyeota.domain.chat.entity.CompanionParticipant;
import com.ktb.moyeota.domain.companion.entity.Companion;
import com.ktb.moyeota.domain.companion.entity.CompanionStatus;
import com.ktb.moyeota.domain.taxipot.entity.TaxiPotMatchRequest;
import com.ktb.moyeota.domain.taxipot.entity.TaxiPotMatchRequestStatus;
import com.ktb.moyeota.domain.taxipot.matching.RegionCell;
import com.ktb.moyeota.domain.user.entity.User;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.IntStream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.test.util.ReflectionTestUtils;

@DataJpaTest
class TaxiPotMatchRequestBatchQueryTest {

    private static final RegionCell GANGNAM = new RegionCell(16_871, 63_599);
    private static final RegionCell PANGYO = new RegionCell(16_860, 63_603);
    private static final LocalDateTime NOW = DEPARTURE_AT.minusHours(1);

    @Autowired
    private TaxiPotMatchRequestRepository repository;

    @Autowired
    private TestEntityManager entityManager;

    private int sequence;

    @Test
    @DisplayName("출발 시각이 지난 찾는 중 요청만 실패로 바꾸고, 다른 상태와 출발 전 요청은 그대로 둔다")
    void failsOnlyDepartedMatching() {
        TaxiPotMatchRequest departed = request(GANGNAM, NOW.minusMinutes(1));
        TaxiPotMatchRequest sameMinute = request(GANGNAM, NOW);
        TaxiPotMatchRequest upcoming = request(GANGNAM, NOW.plusMinutes(30));
        TaxiPotMatchRequest canceled = request(GANGNAM, NOW.minusMinutes(1));
        changeStatus(canceled, TaxiPotMatchRequestStatus.CANCELED);

        int failed = repository.failDeparted(NOW, NOW);

        assertThat(failed).isEqualTo(1);
        assertThat(statusOf(departed)).isEqualTo(TaxiPotMatchRequestStatus.FAILED);
        assertThat(statusOf(sameMinute)).isEqualTo(TaxiPotMatchRequestStatus.MATCHING);
        assertThat(statusOf(upcoming)).isEqualTo(TaxiPotMatchRequestStatus.MATCHING);
        assertThat(statusOf(canceled)).isEqualTo(TaxiPotMatchRequestStatus.CANCELED);
    }

    @Test
    @DisplayName("실패로 바뀐 요청은 찾는 중 중복 검사에서 빠져 같은 사람이 다시 요청할 수 있다")
    void failedRequestReleasesUser() {
        TaxiPotMatchRequest departed = request(GANGNAM, NOW.minusMinutes(1));

        repository.failDeparted(NOW, NOW);
        entityManager.persistAndFlush(matching(departed.getUser(), GANGNAM, NOW.plusMinutes(30)));

        assertThat(repository.existsByUserIdAndStatus(
                departed.getUser().getId(), TaxiPotMatchRequestStatus.MATCHING)).isTrue();
    }

    @Test
    @DisplayName("찾는 중 요청이 있는 칸만 한 번씩 돌려준다")
    void findsRegionsWithMatching() {
        request(GANGNAM, DEPARTURE_AT);
        request(GANGNAM, DEPARTURE_AT);
        request(PANGYO, DEPARTURE_AT);
        TaxiPotMatchRequest matched = request(new RegionCell(1, 1), DEPARTURE_AT);
        changeStatus(matched, TaxiPotMatchRequestStatus.MATCHED);

        List<RegionCell> regions = repository.findMatchingRegions().stream().map(MatchingRegion::toCell).toList();

        assertThat(regions).containsExactlyInAnyOrder(GANGNAM, PANGYO);
    }

    @Test
    @DisplayName("한 칸의 찾는 중 요청만 요청 번호 순으로 돌려준다")
    void findsMatchingInRegion() {
        TaxiPotMatchRequest first = request(GANGNAM, DEPARTURE_AT);
        TaxiPotMatchRequest second = request(GANGNAM, DEPARTURE_AT.plusMinutes(30));
        request(PANGYO, DEPARTURE_AT);
        TaxiPotMatchRequest canceled = request(GANGNAM, DEPARTURE_AT);
        changeStatus(canceled, TaxiPotMatchRequestStatus.CANCELED);

        List<TaxiPotMatchRequest> found = repository.findMatchingInRegion(GANGNAM.row(), GANGNAM.col());

        assertThat(found).extracting(TaxiPotMatchRequest::getId).containsExactly(first.getId(), second.getId());
    }

    @Test
    @DisplayName("자리 남은 모집 중 팟의 멤버 요청 중 아직 참여 중인 사람만 돌려준다")
    void findsOpenPotMembersStillJoined() {
        TaxiPotMatchRequest host = request(GANGNAM, DEPARTURE_AT);
        TaxiPotMatchRequest stayer = request(GANGNAM, DEPARTURE_AT);
        TaxiPotMatchRequest leaver = request(GANGNAM, DEPARTURE_AT);
        Companion pot = matchedPot(List.of(host, stayer, leaver));
        CompanionParticipant leaving = participantOf(pot, leaver.getUser());
        pot.leave(leaving, null);
        entityManager.flush();

        List<TaxiPotMatchRequest> members =
                repository.findOpenPotMembersInRegion(GANGNAM.row(), GANGNAM.col(), NOW);

        assertThat(members).extracting(TaxiPotMatchRequest::getId).containsExactly(host.getId(), stayer.getId());
        assertThat(members).allSatisfy(member -> assertThat(member.getCompanion().getId()).isEqualTo(pot.getId()));
    }

    @Test
    @DisplayName("정원이 찬 팟, 모집 중이 아닌 팟, 다른 칸의 팟, 출발한 팟은 멤버를 돌려주지 않는다")
    void skipsPotsThatCannotTakeMore() {
        matchedPot(requests(GANGNAM, DEPARTURE_AT, 4));
        Companion started = matchedPot(requests(GANGNAM, DEPARTURE_AT, 2));
        ReflectionTestUtils.setField(started, "status", CompanionStatus.IN_PROGRESS);
        matchedPot(requests(PANGYO, DEPARTURE_AT, 2));
        matchedPot(requests(GANGNAM, NOW.minusMinutes(1), 2));
        entityManager.flush();

        List<TaxiPotMatchRequest> members =
                repository.findOpenPotMembersInRegion(GANGNAM.row(), GANGNAM.col(), NOW);

        assertThat(members).isEmpty();
    }

    private List<TaxiPotMatchRequest> requests(RegionCell region, LocalDateTime departureAt, int count) {
        return IntStream.range(0, count).mapToObj(i -> request(region, departureAt)).toList();
    }

    private TaxiPotMatchRequest request(RegionCell region, LocalDateTime departureAt) {
        sequence++;
        User user = entityManager.persist(bankAccountHolder("요청자" + sequence));
        return entityManager.persistAndFlush(matching(user, region, departureAt));
    }

    private static TaxiPotMatchRequest matching(User user, RegionCell region, LocalDateTime departureAt) {
        return TaxiPotMatchRequest.start(user,
                "판교역", new BigDecimal("37.394726"), new BigDecimal("127.111209"),
                "강남역", new BigDecimal("37.498086"), new BigDecimal("127.028001"),
                departureAt, region);
    }

    private Companion matchedPot(List<TaxiPotMatchRequest> members) {
        TaxiPotMatchRequest first = members.getFirst();
        Companion pot = entityManager.persist(Companion.openTaxiPot(first.getUser(),
                first.getOriginName(), first.getOriginLat(), first.getOriginLng(),
                first.getDestName(), first.getDestLat(), first.getDestLng(), first.getDepartureAt()));
        entityManager.persist(CompanionParticipant.join(pot, first.getUser()));
        for (TaxiPotMatchRequest member : members.subList(1, members.size())) {
            entityManager.persist(pot.join(member.getUser(), null));
        }
        members.forEach(member -> member.matchTo(pot));
        entityManager.flush();
        return pot;
    }

    private CompanionParticipant participantOf(Companion pot, User user) {
        return entityManager.getEntityManager()
                .createQuery("SELECT p FROM CompanionParticipant p WHERE p.companion = :pot AND p.user = :user",
                        CompanionParticipant.class)
                .setParameter("pot", pot)
                .setParameter("user", user)
                .getSingleResult();
    }

    private void changeStatus(TaxiPotMatchRequest request, TaxiPotMatchRequestStatus status) {
        ReflectionTestUtils.setField(request, "status", status);
        entityManager.flush();
    }

    private TaxiPotMatchRequestStatus statusOf(TaxiPotMatchRequest request) {
        entityManager.clear();
        return entityManager.find(TaxiPotMatchRequest.class, request.getId()).getStatus();
    }
}
