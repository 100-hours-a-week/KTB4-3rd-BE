package com.ktb.moyeota.domain.companion.repository;

import static com.ktb.moyeota.domain.companion.entity.CompanionStatus.RECRUITING;
import static com.ktb.moyeota.domain.companion.entity.ParticipantOutcome.COMPLETED;
import static com.ktb.moyeota.domain.companion.entity.ParticipantOutcome.INCOMPLETE;
import static com.ktb.moyeota.domain.companion.entity.ParticipantOutcome.PENDING;
import static com.ktb.moyeota.fixture.CompanionFixture.companionPost;
import static com.ktb.moyeota.fixture.CompanionFixture.taxiPot;
import static com.ktb.moyeota.fixture.ParticipantFixture.participant;
import static com.ktb.moyeota.fixture.UserFixture.user;
import static org.assertj.core.api.Assertions.assertThat;

import com.ktb.moyeota.domain.companion.entity.Companion;
import com.ktb.moyeota.domain.user.entity.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;

@DataJpaTest
class CompanionRepositoryTest {

    @Autowired
    private CompanionRepository companionRepository;

    @Autowired
    private TestEntityManager entityManager;

    private User me;

    @BeforeEach
    void setUp() {
        me = persist(user("길동이"));
    }

    @Test
    @DisplayName("참여 중인 택시팟을 찾는다")
    void findsWhilePending() {
        Companion pot = persist(taxiPot(me, RECRUITING));
        persist(participant(pot, me, PENDING));

        assertThat(companionRepository.findTaxiPotForParticipant(pot.getId(), me.getId())).isPresent();
    }

    @Test
    @DisplayName("정산까지 마친 참여자도 찾을 수 있다")
    void findsAfterCompleted() {
        Companion pot = persist(taxiPot(me, RECRUITING));
        persist(participant(pot, me, COMPLETED));

        assertThat(companionRepository.findTaxiPotForParticipant(pot.getId(), me.getId())).isPresent();
    }

    @Test
    @DisplayName("나간 참여자는 찾을 수 없다")
    void hiddenAfterLeaving() {
        Companion pot = persist(taxiPot(me, RECRUITING));
        persist(participant(pot, me, INCOMPLETE));

        assertThat(companionRepository.findTaxiPotForParticipant(pot.getId(), me.getId())).isEmpty();
    }

    @Test
    @DisplayName("없는 id는 찾을 수 없다")
    void emptyForMissingId() {
        Companion pot = persist(taxiPot(me, RECRUITING));
        persist(participant(pot, me, PENDING));

        assertThat(companionRepository.findTaxiPotForParticipant(pot.getId() + 1, me.getId())).isEmpty();
    }

    @Test
    @DisplayName("참여하지 않은 사람은 찾을 수 없다")
    void hiddenFromNonParticipant() {
        User other = persist(user("임꺽정"));
        Companion pot = persist(taxiPot(other, RECRUITING));
        persist(participant(pot, other, PENDING));

        assertThat(companionRepository.findTaxiPotForParticipant(pot.getId(), me.getId())).isEmpty();
    }

    @Test
    @DisplayName("택시팟이 아닌 동행은 찾을 수 없다")
    void hiddenForOtherKinds() {
        Companion post = persist(companionPost(me));
        persist(participant(post, me, PENDING));

        assertThat(companionRepository.findTaxiPotForParticipant(post.getId(), me.getId())).isEmpty();
    }

    private <T> T persist(T entity) {
        return entityManager.persistAndFlush(entity);
    }
}
