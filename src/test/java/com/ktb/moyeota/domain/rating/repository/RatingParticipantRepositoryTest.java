package com.ktb.moyeota.domain.rating.repository;

import static com.ktb.moyeota.domain.chat.entity.OutcomeStatus.COMPLETED;
import static com.ktb.moyeota.domain.chat.entity.OutcomeStatus.INCOMPLETE;
import static com.ktb.moyeota.domain.chat.entity.OutcomeStatus.PENDING;
import static com.ktb.moyeota.fixture.CompanionFixture.taxiPot;
import static com.ktb.moyeota.fixture.ParticipantFixture.participant;
import static com.ktb.moyeota.fixture.UserFixture.user;
import static org.assertj.core.api.Assertions.assertThat;

import com.ktb.moyeota.domain.chat.entity.CompanionParticipant;
import com.ktb.moyeota.domain.companion.entity.Companion;
import com.ktb.moyeota.domain.companion.entity.CompanionStatus;
import com.ktb.moyeota.domain.user.entity.User;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;

@DataJpaTest
class RatingParticipantRepositoryTest {

    @Autowired
    private RatingParticipantRepository ratingParticipantRepository;

    @Autowired
    private TestEntityManager entityManager;

    @Test
    @DisplayName("그 동행을 완주한 참여자만 찾는다")
    void findsCompletedOnly() {
        User host = entityManager.persist(user("방장"));
        User gone = entityManager.persist(user("나간사람"));
        User other = entityManager.persist(user("다른팟"));
        Companion pot = entityManager.persist(taxiPot(host, CompanionStatus.COMPLETED, 2));
        CompanionParticipant completed = entityManager.persist(participant(pot, host, COMPLETED));
        entityManager.persist(participant(pot, gone, INCOMPLETE));
        Companion otherPot = entityManager.persist(taxiPot(other, CompanionStatus.COMPLETED, 1));
        entityManager.persistAndFlush(participant(otherPot, other, COMPLETED));

        assertThat(ratingParticipantRepository.findCompletedParticipants(pot.getId())).containsExactly(completed);
    }

    @Test
    @DisplayName("아직 진행 중인 참여는 찾지 않는다")
    void skipsPending() {
        User host = entityManager.persist(user("방장"));
        Companion pot = entityManager.persist(taxiPot(host, CompanionStatus.IN_PROGRESS, 1));
        entityManager.persistAndFlush(participant(pot, host, PENDING));

        assertThat(ratingParticipantRepository.findCompletedParticipants(pot.getId())).isEmpty();
    }
}
