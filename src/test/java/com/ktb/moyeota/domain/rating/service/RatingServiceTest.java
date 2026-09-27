package com.ktb.moyeota.domain.rating.service;

import static com.ktb.moyeota.domain.chat.entity.OutcomeStatus.COMPLETED;
import static com.ktb.moyeota.domain.chat.entity.OutcomeStatus.INCOMPLETE;
import static com.ktb.moyeota.domain.chat.entity.OutcomeStatus.PENDING;
import static com.ktb.moyeota.fixture.CompanionFixture.taxiPot;
import static com.ktb.moyeota.fixture.ParticipantFixture.participant;
import static com.ktb.moyeota.fixture.UserFixture.user;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;

import com.ktb.moyeota.domain.chat.entity.OutcomeStatus;
import com.ktb.moyeota.domain.companion.entity.Companion;
import com.ktb.moyeota.domain.companion.entity.CompanionStatus;
import com.ktb.moyeota.domain.rating.entity.CompanionRating;
import com.ktb.moyeota.domain.rating.error.RatingErrorCode;
import com.ktb.moyeota.domain.rating.model.RatingSubmitCommand;
import com.ktb.moyeota.domain.rating.model.RatingSubmitCommand.Rating;
import com.ktb.moyeota.domain.user.entity.User;
import com.ktb.moyeota.global.exception.BusinessException;
import java.util.List;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.context.annotation.Import;

@DataJpaTest
@Import(RatingService.class)
class RatingServiceTest {

    @Autowired
    private RatingService ratingService;

    @Autowired
    private TestEntityManager entityManager;

    private User host;
    private User first;
    private User second;
    private Companion pot;

    @BeforeEach
    void setUp() {
        host = entityManager.persist(user("방장"));
        first = entityManager.persist(user("첫째"));
        second = entityManager.persist(user("둘째"));
        pot = entityManager.persist(taxiPot(host, CompanionStatus.COMPLETED, 3));
        persistParticipant(pot, host, COMPLETED);
        persistParticipant(pot, first, COMPLETED);
        persistParticipant(pot, second, COMPLETED);
    }

    @Test
    @DisplayName("완주한 참여자가 함께 탄 사람들을 평가하면 평가가 저장된다")
    void savesRatings() {
        ratingService.submit(host.getId(), pot.getId(), command(new Rating(first.getId(), 5), new Rating(second.getId(), 3)));

        assertThat(ratingsOf(pot))
                .extracting(r -> r.getRater().getId(), r -> r.getRatee().getId(), CompanionRating::getScore)
                .containsExactlyInAnyOrder(
                        tuple(host.getId(), first.getId(), 5),
                        tuple(host.getId(), second.getId(), 3));
    }

    @Test
    @DisplayName("함께 탄 사람 중 한 명이라도 빠지면 RATING_TARGET_MISSING이고 아무것도 저장되지 않는다")
    void rejectsPartialRatings() {
        assertErrorCode(() -> ratingService.submit(host.getId(), pot.getId(), command(new Rating(first.getId(), 4))),
                RatingErrorCode.RATING_TARGET_MISSING);
        assertThat(ratingsOf(pot)).isEmpty();
    }

    @Test
    @DisplayName("다른 참여자의 평가와는 따로 제출할 수 있다")
    void eachRaterSubmitsOnce() {
        ratingService.submit(host.getId(), pot.getId(), command(new Rating(first.getId(), 4), new Rating(second.getId(), 4)));

        ratingService.submit(first.getId(), pot.getId(), command(new Rating(host.getId(), 5), new Rating(second.getId(), 5)));

        assertThat(ratingsOf(pot)).hasSize(4);
    }

    @Test
    @DisplayName("이미 제출한 사람이 다시 제출하면 ALREADY_RATED다")
    void alreadyRated() {
        ratingService.submit(host.getId(), pot.getId(), command(new Rating(first.getId(), 4), new Rating(second.getId(), 4)));

        assertErrorCode(() -> ratingService.submit(host.getId(), pot.getId(),
                        command(new Rating(first.getId(), 1), new Rating(second.getId(), 1))),
                RatingErrorCode.ALREADY_RATED);
    }

    @Test
    @DisplayName("참여하지 않은 동행이면 RATABLE_COMPANION_NOT_FOUND다")
    void notParticipant() {
        User stranger = entityManager.persist(user("구경꾼"));

        assertErrorCode(() -> ratingService.submit(stranger.getId(), pot.getId(), command(new Rating(first.getId(), 4))),
                RatingErrorCode.RATABLE_COMPANION_NOT_FOUND);
    }

    @Test
    @DisplayName("아직 운행이 끝나지 않았으면 RATABLE_COMPANION_NOT_FOUND다")
    void notCompletedYet() {
        Companion riding = entityManager.persist(taxiPot(host, CompanionStatus.IN_PROGRESS, 2));
        persistParticipant(riding, host, PENDING);
        persistParticipant(riding, first, PENDING);

        assertErrorCode(() -> ratingService.submit(host.getId(), riding.getId(), command(new Rating(first.getId(), 4))),
                RatingErrorCode.RATABLE_COMPANION_NOT_FOUND);
    }

    @Test
    @DisplayName("중간에 나간 사람은 평가할 수 없고 평가 대상도 될 수 없다")
    void leftParticipant() {
        User gone = entityManager.persist(user("나간사람"));
        persistParticipant(pot, gone, INCOMPLETE);

        assertErrorCode(() -> ratingService.submit(gone.getId(), pot.getId(), command(new Rating(first.getId(), 4))),
                RatingErrorCode.RATABLE_COMPANION_NOT_FOUND);
        assertErrorCode(() -> ratingService.submit(host.getId(), pot.getId(),
                        command(new Rating(first.getId(), 4), new Rating(second.getId(), 4), new Rating(gone.getId(), 1))),
                RatingErrorCode.INVALID_RATING_TARGET);
    }

    @Test
    @DisplayName("자기 자신은 평가할 수 없다")
    void selfRating() {
        assertErrorCode(() -> ratingService.submit(host.getId(), pot.getId(), command(new Rating(host.getId(), 5))),
                RatingErrorCode.INVALID_RATING_TARGET);
    }

    @Test
    @DisplayName("같은 사람을 두 번 넣으면 INVALID_RATING_TARGET이고 아무것도 저장되지 않는다")
    void duplicateTarget() {
        assertErrorCode(() -> ratingService.submit(host.getId(), pot.getId(),
                        command(new Rating(first.getId(), 5), new Rating(first.getId(), 1))),
                RatingErrorCode.INVALID_RATING_TARGET);
        assertThat(ratingsOf(pot)).isEmpty();
    }

    private void persistParticipant(Companion companion, User user, OutcomeStatus outcome) {
        entityManager.persistAndFlush(participant(companion, user, outcome));
    }

    private static RatingSubmitCommand command(Rating... ratings) {
        return new RatingSubmitCommand(List.of(ratings));
    }

    private List<CompanionRating> ratingsOf(Companion companion) {
        entityManager.flush();
        return entityManager.getEntityManager()
                .createQuery("SELECT r FROM CompanionRating r WHERE r.companion.id = :id", CompanionRating.class)
                .setParameter("id", companion.getId())
                .getResultList();
    }

    private static void assertErrorCode(ThrowingCallable call, RatingErrorCode expected) {
        assertThatThrownBy(call)
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(expected);
    }
}
