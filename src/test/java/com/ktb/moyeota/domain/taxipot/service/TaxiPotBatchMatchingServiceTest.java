package com.ktb.moyeota.domain.taxipot.service;

import static com.ktb.moyeota.fixture.MatchRequestFixture.DEPARTURE_AT;
import static com.ktb.moyeota.fixture.UserFixture.bankAccountHolder;
import static org.assertj.core.api.Assertions.assertThat;

import com.ktb.moyeota.domain.chat.entity.ChatRoom;
import com.ktb.moyeota.domain.chat.entity.CompanionParticipant;
import com.ktb.moyeota.domain.chat.service.ChatSystemMessageService;
import com.ktb.moyeota.domain.companion.entity.Companion;
import com.ktb.moyeota.domain.taxipot.entity.TaxiPotMatchRequest;
import com.ktb.moyeota.domain.taxipot.entity.TaxiPotMatchRequestStatus;
import com.ktb.moyeota.domain.taxipot.matching.RegionCell;
import com.ktb.moyeota.domain.taxipot.service.TaxiPotBatchMatchingService.BatchMatchingSummary;
import com.ktb.moyeota.domain.user.entity.User;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;

@DataJpaTest
@Import({TaxiPotBatchMatchingService.class, TaxiPotMatchReflector.class, ChatSystemMessageService.class,
        TaxiPotBatchMatchingServiceTest.FixedClock.class})
class TaxiPotBatchMatchingServiceTest {

    private static final LocalDateTime NOW = DEPARTURE_AT.minusHours(1);
    private static final RegionCell GANGNAM = new RegionCell(16_871, 63_599);
    private static final RegionCell PANGYO = new RegionCell(16_860, 63_603);

    private static final Spot PANGYO_STATION = new Spot("판교역", "37.394726", "127.111209");
    private static final Spot PANGYO_STATION_NORTH = new Spot("판교역 북쪽 300m", "37.397426", "127.111209");
    private static final Spot GANGNAM_STATION = new Spot("강남역", "37.498086", "127.028001");

    @Autowired
    private TaxiPotBatchMatchingService service;

    @Autowired
    private TestEntityManager entityManager;

    private int sequence;

    @Test
    @DisplayName("같은 칸, 같은 출발 시각의 가까운 찾는 중 요청들을 한 팟으로 묶어 반영한다")
    void matchesNearbyRequestsIntoOnePot() {
        TaxiPotMatchRequest first = request(GANGNAM, PANGYO_STATION, DEPARTURE_AT);
        TaxiPotMatchRequest second = request(GANGNAM, PANGYO_STATION, DEPARTURE_AT);

        BatchMatchingSummary summary = service.runOnce();

        assertThat(summary.applied()).isEqualTo(1);
        List<TaxiPotMatchRequest> reloaded = reload(first, second);
        assertThat(reloaded).allSatisfy(request -> {
            assertThat(request.getStatus()).isEqualTo(TaxiPotMatchRequestStatus.MATCHED);
            assertThat(request.getCompanion()).isNotNull();
        });
        assertThat(reloaded.get(0).getCompanion().getId()).isEqualTo(reloaded.get(1).getCompanion().getId());
        assertThat(reloaded.get(0).getCompanion().getCurrentCount()).isEqualTo(2);
    }

    @Test
    @DisplayName("다른 칸이거나 출발 시각이 다르면 묶지 않고 찾는 중으로 남긴다")
    void keepsRequestsInOtherRegionsOrTimesApart() {
        TaxiPotMatchRequest gangnam = request(GANGNAM, PANGYO_STATION, DEPARTURE_AT);
        TaxiPotMatchRequest pangyo = request(PANGYO, PANGYO_STATION, DEPARTURE_AT);
        TaxiPotMatchRequest later = request(GANGNAM, PANGYO_STATION, DEPARTURE_AT.plusMinutes(30));

        BatchMatchingSummary summary = service.runOnce();

        assertThat(summary.applied()).isZero();
        assertThat(summary.regions()).isEqualTo(2);
        assertThat(reload(gangnam, pangyo, later))
                .allSatisfy(request -> assertThat(request.getStatus()).isEqualTo(TaxiPotMatchRequestStatus.MATCHING));
        assertThat(companionCount()).isZero();
    }

    @Test
    @DisplayName("출발 시각이 지난 찾는 중 요청은 묶지 않고 실패로 끝낸다")
    void failsDepartedRequests() {
        TaxiPotMatchRequest first = request(GANGNAM, PANGYO_STATION, NOW.minusMinutes(1));
        TaxiPotMatchRequest second = request(GANGNAM, PANGYO_STATION, NOW.minusMinutes(1));

        BatchMatchingSummary summary = service.runOnce();

        assertThat(summary.failed()).isEqualTo(2);
        assertThat(summary.applied()).isZero();
        assertThat(reload(first, second))
                .allSatisfy(request -> assertThat(request.getStatus()).isEqualTo(TaxiPotMatchRequestStatus.FAILED));
        assertThat(companionCount()).isZero();
    }

    @Test
    @DisplayName("자리 남은 기존 팟이 있으면 새 찾는 중 요청을 그 팟에 합류시킨다")
    void joinsOpenPot() {
        Companion pot = matchedPot(List.of(
                request(GANGNAM, PANGYO_STATION, DEPARTURE_AT),
                request(GANGNAM, PANGYO_STATION, DEPARTURE_AT)));
        TaxiPotMatchRequest joiner = request(GANGNAM, PANGYO_STATION, DEPARTURE_AT);

        BatchMatchingSummary summary = service.runOnce();

        assertThat(summary.applied()).isEqualTo(1);
        TaxiPotMatchRequest reloaded = reload(joiner).getFirst();
        assertThat(reloaded.getStatus()).isEqualTo(TaxiPotMatchRequestStatus.MATCHED);
        assertThat(reloaded.getCompanion().getId()).isEqualTo(pot.getId());
        assertThat(reloaded.getCompanion().getCurrentCount()).isEqualTo(3);
        assertThat(companionCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("팟에서 나간 사람의 위치는 합류 판정에 쓰지 않는다")
    void ignoresMembersWhoLeft() {
        TaxiPotMatchRequest host = request(GANGNAM, PANGYO_STATION, DEPARTURE_AT);
        TaxiPotMatchRequest leaver = request(GANGNAM, PANGYO_STATION_NORTH, DEPARTURE_AT);
        Companion pot = matchedPot(List.of(host, leaver));
        pot.leave(participantOf(pot, leaver.getUser()), null);
        entityManager.flush();
        TaxiPotMatchRequest joiner = request(GANGNAM, PANGYO_STATION, DEPARTURE_AT);

        service.runOnce();

        TaxiPotMatchRequest reloaded = reload(joiner).getFirst();
        assertThat(reloaded.getStatus()).isEqualTo(TaxiPotMatchRequestStatus.MATCHED);
        assertThat(reloaded.getCompanion().getId()).isEqualTo(pot.getId());
    }

    @Test
    @DisplayName("한 번 묶인 요청은 다음 주기에 다시 반영되지 않는다")
    void doesNotApplyTwice() {
        request(GANGNAM, PANGYO_STATION, DEPARTURE_AT);
        request(GANGNAM, PANGYO_STATION, DEPARTURE_AT);
        service.runOnce();

        BatchMatchingSummary second = service.runOnce();

        assertThat(second.regions()).isZero();
        assertThat(second.applied()).isZero();
        assertThat(companionCount()).isEqualTo(1);
    }

    private TaxiPotMatchRequest request(RegionCell region, Spot origin, LocalDateTime departureAt) {
        sequence++;
        User user = entityManager.persist(bankAccountHolder("요청자" + sequence));
        return entityManager.persistAndFlush(TaxiPotMatchRequest.start(user,
                origin.name(), origin.lat(), origin.lng(),
                GANGNAM_STATION.name(), GANGNAM_STATION.lat(), GANGNAM_STATION.lng(),
                departureAt, region));
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
        entityManager.persist(ChatRoom.create(pot));
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

    private List<TaxiPotMatchRequest> reload(TaxiPotMatchRequest... requests) {
        entityManager.flush();
        entityManager.clear();
        return Arrays.stream(requests)
                .map(request -> entityManager.find(TaxiPotMatchRequest.class, request.getId()))
                .toList();
    }

    private long companionCount() {
        entityManager.flush();
        return entityManager.getEntityManager()
                .createQuery("SELECT count(c) FROM Companion c", Long.class)
                .getSingleResult();
    }

    private record Spot(String name, BigDecimal lat, BigDecimal lng) {

        Spot(String name, String lat, String lng) {
            this(name, new BigDecimal(lat), new BigDecimal(lng));
        }
    }

    @TestConfiguration
    static class FixedClock {

        @Bean
        Clock clock() {
            ZoneId kst = ZoneId.of("Asia/Seoul");
            return Clock.fixed(NOW.atZone(kst).toInstant(), kst);
        }
    }
}
