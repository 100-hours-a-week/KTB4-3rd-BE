package com.ktb.moyeota.domain.taxipot.entity;

import static com.ktb.moyeota.fixture.UserFixture.user;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ktb.moyeota.domain.taxipot.matching.RegionCell;
import com.ktb.moyeota.domain.user.entity.User;
import jakarta.persistence.PersistenceException;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;

@DataJpaTest
class TaxiPotMatchRequestTest {

    private static final LocalDateTime DEPARTURE_AT = LocalDateTime.of(2026, 10, 22, 18, 30);
    private static final RegionCell GANGNAM = new RegionCell(16871, 63599);

    @Autowired
    private TestEntityManager entityManager;

    private User requester;

    @BeforeEach
    void setUp() {
        requester = entityManager.persistAndFlush(user("요청자"));
    }

    @Test
    @DisplayName("매칭 요청은 찾는 중 상태로 출발지·도착지·출발 시각·지역 칸과 함께 저장된다")
    void startsMatching() {
        Long id = entityManager.persistAndFlush(request(requester)).getId();

        entityManager.clear();
        TaxiPotMatchRequest saved = entityManager.find(TaxiPotMatchRequest.class, id);
        assertThat(saved.getStatus()).isEqualTo(TaxiPotMatchRequestStatus.MATCHING);
        assertThat(saved.getUser().getId()).isEqualTo(requester.getId());
        assertThat(saved.getOriginName()).isEqualTo("판교역");
        assertThat(saved.getDestName()).isEqualTo("강남역");
        assertThat(saved.getDepartureAt()).isEqualTo(DEPARTURE_AT);
        assertThat(saved.getRegionRow()).isEqualTo(GANGNAM.row());
        assertThat(saved.getRegionCol()).isEqualTo(GANGNAM.col());
        assertThat(saved.getCompanion()).isNull();
        assertThat(saved.getCreatedAt()).isNotNull();
        assertThat(saved.getUpdatedAt()).isNotNull();
        assertThat(matchingUserIdOf(id)).isEqualTo(requester.getId());
    }

    @Test
    @DisplayName("찾는 중 요청이 있으면 같은 사람의 두 번째 찾는 중 요청은 DB 가 막는다")
    void rejectsSecondMatchingRequest() {
        entityManager.persistAndFlush(request(requester));

        assertThatThrownBy(() -> entityManager.persistAndFlush(request(requester)))
                .isInstanceOf(PersistenceException.class);
    }

    @Test
    @DisplayName("끝난 요청은 중복 검사에서 빠져 다시 요청할 수 있고, 끝난 요청끼리는 여러 개 남는다")
    void allowsRequestAfterFinished() {
        Long matched = entityManager.persistAndFlush(request(requester)).getId();
        changeStatus(matched, TaxiPotMatchRequestStatus.MATCHED);
        Long canceled = entityManager.persistAndFlush(request(requester)).getId();
        changeStatus(canceled, TaxiPotMatchRequestStatus.CANCELED);

        Long matching = entityManager.persistAndFlush(request(requester)).getId();

        assertThat(matchingUserIdOf(matched)).isNull();
        assertThat(matchingUserIdOf(canceled)).isNull();
        assertThat(matchingUserIdOf(matching)).isEqualTo(requester.getId());
    }

    @Test
    @DisplayName("다른 사람의 찾는 중 요청과는 겹치지 않는다")
    void allowsOtherUsersMatchingRequest() {
        User other = entityManager.persistAndFlush(user("다른요청자"));
        Long mine = entityManager.persistAndFlush(request(requester)).getId();

        Long others = entityManager.persistAndFlush(request(other)).getId();

        assertThat(matchingUserIdOf(mine)).isEqualTo(requester.getId());
        assertThat(matchingUserIdOf(others)).isEqualTo(other.getId());
    }

    private static TaxiPotMatchRequest request(User user) {
        return TaxiPotMatchRequest.start(user,
                "판교역", new BigDecimal("37.394500"), new BigDecimal("127.111200"),
                "강남역", new BigDecimal("37.497900"), new BigDecimal("127.027600"),
                DEPARTURE_AT, GANGNAM);
    }

    private void changeStatus(Long requestId, TaxiPotMatchRequestStatus status) {
        entityManager.getEntityManager()
                .createQuery("UPDATE TaxiPotMatchRequest r SET r.status = :status WHERE r.id = :id")
                .setParameter("status", status)
                .setParameter("id", requestId)
                .executeUpdate();
    }

    private Long matchingUserIdOf(Long requestId) {
        Object value = entityManager.getEntityManager()
                .createNativeQuery("SELECT matching_user_id FROM taxi_pot_match_requests WHERE id = :id")
                .setParameter("id", requestId)
                .getSingleResult();
        return value == null ? null : ((Number) value).longValue();
    }
}
