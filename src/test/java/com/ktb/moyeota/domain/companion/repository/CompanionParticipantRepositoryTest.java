package com.ktb.moyeota.domain.companion.repository;

import static com.ktb.moyeota.domain.companion.entity.CompanionStatus.CANCELED;
import static com.ktb.moyeota.domain.companion.entity.CompanionStatus.COMPLETED;
import static com.ktb.moyeota.domain.companion.entity.CompanionStatus.RECRUITING;
import static com.ktb.moyeota.domain.companion.entity.ParticipantOutcome.INCOMPLETE;
import static com.ktb.moyeota.domain.companion.entity.ParticipantOutcome.PENDING;
import static com.ktb.moyeota.fixture.CompanionFixture.companionPost;
import static com.ktb.moyeota.fixture.CompanionFixture.taxiPot;
import static com.ktb.moyeota.fixture.ParticipantFixture.participant;
import static com.ktb.moyeota.fixture.UserFixture.user;
import static org.assertj.core.api.Assertions.assertThat;

import com.ktb.moyeota.domain.companion.entity.Companion;
import com.ktb.moyeota.domain.companion.entity.ParticipantOutcome;
import com.ktb.moyeota.domain.user.entity.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;

@DataJpaTest
class CompanionParticipantRepositoryTest {

    @Autowired
    private CompanionParticipantRepository companionParticipantRepository;

    @Autowired
    private TestEntityManager entityManager;

    private User me;

    @BeforeEach
    void setUp() {
        me = persist(user("길동이"));
    }

    @Test
    @DisplayName("PENDING으로 참여 중인 택시팟을 찾는다")
    void findsPendingTaxiPot() {
        Companion pot = persist(taxiPot(me, RECRUITING));
        persist(participant(pot, me, PENDING));

        assertThat(companionParticipantRepository.findCurrentTaxiPot(me.getId()))
                .get().extracting(Companion::getId).isEqualTo(pot.getId());
    }

    @Test
    @DisplayName("운행이 끝나 정산 중인 택시팟도 PENDING이면 진행 중이다")
    void settlingTaxiPotIsStillCurrent() {
        Companion pot = persist(taxiPot(me, COMPLETED));
        persist(participant(pot, me, PENDING));

        assertThat(companionParticipantRepository.findCurrentTaxiPot(me.getId())).isPresent();
    }

    @Test
    @DisplayName("참여한 적이 없으면 비어 있다")
    void emptyWhenNeverJoined() {
        assertThat(companionParticipantRepository.findCurrentTaxiPot(me.getId())).isEmpty();
    }

    @Test
    @DisplayName("나갔거나 완주한 택시팟은 진행 중이 아니다")
    void ignoresFinishedParticipation() {
        persist(participant(persist(taxiPot(me, CANCELED)), me, INCOMPLETE));
        persist(participant(persist(taxiPot(me, COMPLETED)), me, ParticipantOutcome.COMPLETED));

        assertThat(companionParticipantRepository.findCurrentTaxiPot(me.getId())).isEmpty();
    }

    @Test
    @DisplayName("택시팟이 아닌 동행은 진행 중인 택시팟이 아니다")
    void ignoresOtherKinds() {
        persist(participant(persist(companionPost(me)), me, PENDING));

        assertThat(companionParticipantRepository.findCurrentTaxiPot(me.getId())).isEmpty();
    }

    @Test
    @DisplayName("다른 사람의 참여는 보지 않는다")
    void ignoresOtherUsers() {
        User other = persist(user("임꺽정"));
        persist(participant(persist(taxiPot(other, RECRUITING)), other, PENDING));

        assertThat(companionParticipantRepository.findCurrentTaxiPot(me.getId())).isEmpty();
    }

    private <T> T persist(T entity) {
        return entityManager.persistAndFlush(entity);
    }
}
