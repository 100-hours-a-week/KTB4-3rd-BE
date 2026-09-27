package com.ktb.moyeota.domain.rating.repository;

import static com.ktb.moyeota.fixture.CompanionFixture.taxiPot;
import static com.ktb.moyeota.fixture.UserFixture.user;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ktb.moyeota.domain.companion.entity.Companion;
import com.ktb.moyeota.domain.companion.entity.CompanionStatus;
import com.ktb.moyeota.domain.rating.entity.CompanionRating;
import com.ktb.moyeota.domain.user.entity.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.dao.DataIntegrityViolationException;

@DataJpaTest
class CompanionRatingRepositoryTest {

    @Autowired
    private CompanionRatingRepository companionRatingRepository;

    @Autowired
    private TestEntityManager entityManager;

    private User rater;
    private User ratee;
    private Companion pot;

    @BeforeEach
    void setUp() {
        rater = entityManager.persist(user("평가자"));
        ratee = entityManager.persist(user("피평가자"));
        pot = entityManager.persist(taxiPot(rater, CompanionStatus.COMPLETED, 2));
    }

    @Test
    @DisplayName("그 동행에 내가 남긴 평가가 있으면 있다고 답한다")
    void existsWhenRated() {
        entityManager.persistAndFlush(CompanionRating.of(pot, rater, ratee, 5));

        assertThat(companionRatingRepository.existsByCompanionIdAndRaterId(pot.getId(), rater.getId())).isTrue();
        assertThat(companionRatingRepository.existsByCompanionIdAndRaterId(pot.getId(), ratee.getId())).isFalse();
    }

    @Test
    @DisplayName("같은 동행에서 같은 사람을 두 번 평가하면 유니크 제약에 막힌다")
    void uniqueRating() {
        companionRatingRepository.saveAndFlush(CompanionRating.of(pot, rater, ratee, 5));

        assertThatThrownBy(() -> companionRatingRepository.saveAndFlush(CompanionRating.of(pot, rater, ratee, 1)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }
}
