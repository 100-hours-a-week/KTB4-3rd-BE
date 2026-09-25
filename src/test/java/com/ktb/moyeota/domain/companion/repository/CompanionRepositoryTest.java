package com.ktb.moyeota.domain.companion.repository;

import static com.ktb.moyeota.domain.companion.entity.CompanionStatus.IN_PROGRESS;
import static com.ktb.moyeota.domain.companion.entity.CompanionStatus.RECRUITING;
import static com.ktb.moyeota.fixture.CompanionFixture.DEPARTURE_AT;
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
import org.junit.jupiter.api.Nested;
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

    @Nested
    @DisplayName("운행 시작")
    class StartRide {

        @Test
        @DisplayName("모집 중이고 2명 이상이며 출발 시각이 지났으면 방장이 시작할 수 있다")
        void starts() {
            Companion pot = persist(taxiPot(me, RECRUITING, 2));

            assertThat(companionRepository.startRide(pot.getId(), me.getId(), DEPARTURE_AT)).isEqualTo(1);
        }

        @Test
        @DisplayName("혼자면 시작하지 않는다")
        void alone() {
            Companion pot = persist(taxiPot(me, RECRUITING, 1));

            assertThat(companionRepository.startRide(pot.getId(), me.getId(), DEPARTURE_AT)).isZero();
        }

        @Test
        @DisplayName("출발 시각 전이면 시작하지 않는다")
        void beforeDeparture() {
            Companion pot = persist(taxiPot(me, RECRUITING, 2));

            assertThat(companionRepository.startRide(pot.getId(), me.getId(), DEPARTURE_AT.minusSeconds(1))).isZero();
        }

        @Test
        @DisplayName("방장이 아니면 시작하지 않는다")
        void notHost() {
            User other = persist(user("임꺽정"));
            Companion pot = persist(taxiPot(me, RECRUITING, 2));

            assertThat(companionRepository.startRide(pot.getId(), other.getId(), DEPARTURE_AT)).isZero();
        }

        @Test
        @DisplayName("이미 운행 중이면 다시 시작하지 않는다")
        void alreadyStarted() {
            Companion pot = persist(taxiPot(me, IN_PROGRESS, 2));

            assertThat(companionRepository.startRide(pot.getId(), me.getId(), DEPARTURE_AT)).isZero();
        }
    }

    @Nested
    @DisplayName("운행 종료")
    class CompleteRide {

        @Test
        @DisplayName("운행 중이면 방장이 종료할 수 있다")
        void completes() {
            Companion pot = persist(taxiPot(me, IN_PROGRESS, 2));

            assertThat(companionRepository.completeRide(pot.getId(), me.getId())).isEqualTo(1);
        }

        @Test
        @DisplayName("모집 중이면 운행을 건너뛰고 종료할 수 없다")
        void notStarted() {
            Companion pot = persist(taxiPot(me, RECRUITING, 2));

            assertThat(companionRepository.completeRide(pot.getId(), me.getId())).isZero();
        }
    }

    private <T> T persist(T entity) {
        return entityManager.persistAndFlush(entity);
    }
}
