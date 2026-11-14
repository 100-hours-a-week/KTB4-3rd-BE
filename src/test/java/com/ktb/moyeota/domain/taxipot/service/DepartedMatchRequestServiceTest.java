package com.ktb.moyeota.domain.taxipot.service;

import static com.ktb.moyeota.fixture.MatchRequestFixture.DEPARTURE_AT;
import static com.ktb.moyeota.fixture.UserFixture.bankAccountHolder;
import static org.assertj.core.api.Assertions.assertThat;

import com.ktb.moyeota.domain.taxipot.entity.TaxiPotMatchRequest;
import com.ktb.moyeota.domain.taxipot.entity.TaxiPotMatchRequestStatus;
import com.ktb.moyeota.domain.taxipot.matching.RegionCell;
import com.ktb.moyeota.domain.user.entity.User;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;

@DataJpaTest
@Import({DepartedMatchRequestService.class, DepartedMatchRequestServiceTest.FixedClock.class})
class DepartedMatchRequestServiceTest {

    private static final LocalDateTime NOW = DEPARTURE_AT.plusSeconds(30);
    private static final RegionCell PANGYO = new RegionCell(16_860, 63_603);
    private static final BigDecimal LAT = new BigDecimal("37.394726");
    private static final BigDecimal LNG = new BigDecimal("127.111209");

    @Autowired
    private DepartedMatchRequestService service;

    @Autowired
    private TestEntityManager entityManager;

    private int sequence;

    @Test
    @DisplayName("출발 시각(분)이 지난 찾는 중 요청만 실패로 끝내고, 지금 분과 그 뒤 출발은 남긴다")
    void failsOnlyDepartedMatchingRequests() {
        TaxiPotMatchRequest departed = request(DEPARTURE_AT.minusMinutes(1));
        TaxiPotMatchRequest thisMinute = request(DEPARTURE_AT);
        TaxiPotMatchRequest later = request(DEPARTURE_AT.plusMinutes(30));

        int failed = service.failDeparted();

        entityManager.clear();
        assertThat(failed).isEqualTo(1);
        assertThat(statusOf(departed)).isEqualTo(TaxiPotMatchRequestStatus.FAILED);
        assertThat(statusOf(thisMinute)).isEqualTo(TaxiPotMatchRequestStatus.MATCHING);
        assertThat(statusOf(later)).isEqualTo(TaxiPotMatchRequestStatus.MATCHING);
    }

    private TaxiPotMatchRequest request(LocalDateTime departureAt) {
        sequence++;
        User user = entityManager.persist(bankAccountHolder("요청자" + sequence));
        return entityManager.persistAndFlush(TaxiPotMatchRequest.start(user,
                "판교역", LAT, LNG, "강남역", LAT, LNG, departureAt, PANGYO));
    }

    private TaxiPotMatchRequestStatus statusOf(TaxiPotMatchRequest request) {
        return entityManager.find(TaxiPotMatchRequest.class, request.getId()).getStatus();
    }

    static class FixedClock {

        @Bean
        Clock clock() {
            ZoneId kst = ZoneId.of("Asia/Seoul");
            return Clock.fixed(NOW.atZone(kst).toInstant(), kst);
        }
    }
}
