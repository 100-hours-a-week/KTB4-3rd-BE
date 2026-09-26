package com.ktb.moyeota.domain.taxipot.repository;

import static com.ktb.moyeota.domain.chat.entity.OutcomeStatus.COMPLETED;
import static com.ktb.moyeota.domain.chat.entity.OutcomeStatus.INCOMPLETE;
import static com.ktb.moyeota.domain.chat.entity.OutcomeStatus.PENDING;
import static com.ktb.moyeota.domain.companion.entity.CompanionStatus.CANCELED;
import static com.ktb.moyeota.domain.companion.entity.CompanionStatus.RECRUITING;
import static com.ktb.moyeota.fixture.CompanionFixture.DEPARTURE_AT;
import static com.ktb.moyeota.fixture.CompanionFixture.companionPost;
import static com.ktb.moyeota.fixture.CompanionFixture.taxiPot;
import static com.ktb.moyeota.fixture.ParticipantFixture.participant;
import static com.ktb.moyeota.fixture.UserFixture.user;
import static org.assertj.core.api.Assertions.assertThat;

import com.ktb.moyeota.domain.chat.entity.CompanionParticipant;
import com.ktb.moyeota.domain.companion.entity.Companion;
import com.ktb.moyeota.domain.companion.entity.CompanionStatus;
import com.ktb.moyeota.domain.user.entity.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;

@DataJpaTest
class TaxiPotParticipantRepositoryTest {

    @Autowired
    private TaxiPotParticipantRepository taxiPotParticipantRepository;

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

        assertThat(taxiPotParticipantRepository.findCurrentTaxiPot(me.getId()))
                .get().extracting(Companion::getId).isEqualTo(pot.getId());
    }

    @Test
    @DisplayName("운행이 끝나 정산 중인 택시팟도 PENDING이면 진행 중이다")
    void settlingTaxiPotIsStillCurrent() {
        Companion pot = persist(taxiPot(me, CompanionStatus.COMPLETED));
        persist(participant(pot, me, PENDING));

        assertThat(taxiPotParticipantRepository.findCurrentTaxiPot(me.getId())).isPresent();
    }

    @Test
    @DisplayName("참여한 적이 없으면 비어 있다")
    void emptyWhenNeverJoined() {
        assertThat(taxiPotParticipantRepository.findCurrentTaxiPot(me.getId())).isEmpty();
    }

    @Test
    @DisplayName("나갔거나 완주한 택시팟은 진행 중이 아니다")
    void ignoresFinishedParticipation() {
        persist(participant(persist(taxiPot(me, CANCELED)), me, INCOMPLETE));
        persist(participant(persist(taxiPot(me, CompanionStatus.COMPLETED)), me, COMPLETED));

        assertThat(taxiPotParticipantRepository.findCurrentTaxiPot(me.getId())).isEmpty();
    }

    @Test
    @DisplayName("택시팟이 아닌 동행은 진행 중인 택시팟이 아니다")
    void ignoresOtherKinds() {
        persist(participant(persist(companionPost(me)), me, PENDING));

        assertThat(taxiPotParticipantRepository.findCurrentTaxiPot(me.getId())).isEmpty();
    }

    @Test
    @DisplayName("다른 사람의 참여는 보지 않는다")
    void ignoresOtherUsers() {
        User other = persist(user("임꺽정"));
        persist(participant(persist(taxiPot(other, RECRUITING)), other, PENDING));

        assertThat(taxiPotParticipantRepository.findCurrentTaxiPot(me.getId())).isEmpty();
    }


    @Nested
    @DisplayName("다음 방장 찾기")
    class FindNextHost {

        @Test
        @DisplayName("나가는 사람을 빼고 가장 먼저 들어온 진행 중 참여자를 찾는다")
        void earliestPendingExceptLeaver() {
            Companion pot = persist(taxiPot(me, RECRUITING));
            persist(participant(pot, me, DEPARTURE_AT.minusMinutes(30)));
            CompanionParticipant second = persist(participant(pot, persist(user("둘째")), DEPARTURE_AT.minusMinutes(20)));
            persist(participant(pot, persist(user("셋째")), DEPARTURE_AT.minusMinutes(10)));

            assertThat(taxiPotParticipantRepository.findNextHost(pot.getId(), me.getId()))
                    .get().extracting(CompanionParticipant::getId).isEqualTo(second.getId());
        }

        @Test
        @DisplayName("먼저 들어왔어도 이미 나간 사람은 건너뛴다")
        void skipsLeftParticipants() {
            Companion pot = persist(taxiPot(me, RECRUITING));
            persist(participant(pot, me, PENDING));
            persist(participant(pot, persist(user("나간사람")), INCOMPLETE));
            CompanionParticipant stayed = persist(participant(pot, persist(user("남은사람")), DEPARTURE_AT));

            assertThat(taxiPotParticipantRepository.findNextHost(pot.getId(), me.getId()))
                    .get().extracting(CompanionParticipant::getId).isEqualTo(stayed.getId());
        }

        @Test
        @DisplayName("남은 사람이 없으면 비어 있다")
        void emptyWhenAlone() {
            Companion pot = persist(taxiPot(me, RECRUITING));
            persist(participant(pot, me, PENDING));

            assertThat(taxiPotParticipantRepository.findNextHost(pot.getId(), me.getId())).isEmpty();
        }
    }

    private <T> T persist(T entity) {
        return entityManager.persistAndFlush(entity);
    }
}
