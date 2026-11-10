package com.ktb.moyeota.domain.notification.repository;

import static com.ktb.moyeota.domain.chat.entity.OutcomeStatus.COMPLETED;
import static com.ktb.moyeota.domain.chat.entity.OutcomeStatus.INCOMPLETE;
import static com.ktb.moyeota.domain.chat.entity.OutcomeStatus.PENDING;
import static com.ktb.moyeota.fixture.CompanionFixture.carpool;
import static com.ktb.moyeota.fixture.CompanionFixture.taxiPot;
import static com.ktb.moyeota.fixture.ParticipantFixture.participant;
import static com.ktb.moyeota.fixture.UserFixture.bankAccountHolder;
import static org.assertj.core.api.Assertions.assertThat;

import com.ktb.moyeota.domain.chat.entity.ChatRoom;
import com.ktb.moyeota.domain.companion.entity.Companion;
import com.ktb.moyeota.domain.companion.entity.CompanionStatus;
import com.ktb.moyeota.domain.settlement.entity.Settlement;
import com.ktb.moyeota.domain.user.entity.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;

@DataJpaTest
class SettlementReminderRepositoryTest {

    @Autowired
    private SettlementReminderRepository reminderRepository;

    @Autowired
    private TestEntityManager entityManager;

    private User host;

    @BeforeEach
    void setUp() {
        host = entityManager.persist(bankAccountHolder("방장"));
    }

    @Nested
    @DisplayName("findSettlementIdsToRemind")
    class FindSettlementIdsToRemind {

        @Test
        @DisplayName("운행이 끝난 택시팟 중 정산이 REQUESTED로 남은 것만 조회한다")
        void onlyRequestedTaxiPot() {
            Settlement requested = requested(taxiPot(host, CompanionStatus.COMPLETED, 3));
            settlement(taxiPot(host, CompanionStatus.COMPLETED, 3));
            Settlement completed = requested(taxiPot(host, CompanionStatus.COMPLETED, 3));
            completed.complete();
            requested(carpool(host, CompanionStatus.COMPLETED, 3));
            entityManager.flush();

            assertThat(reminderRepository.findSettlementIdsToRemind()).containsExactly(requested.getId());
        }
    }

    @Nested
    @DisplayName("findRequestedForUpdate")
    class FindRequestedForUpdate {

        @Test
        @DisplayName("REQUESTED인 정산은 조회된다")
        void requested() {
            Settlement settlement = SettlementReminderRepositoryTest.this.requested(
                    taxiPot(host, CompanionStatus.COMPLETED, 3));
            entityManager.flush();

            assertThat(reminderRepository.findRequestedForUpdate(settlement.getId())).isPresent();
        }

        @Test
        @DisplayName("정산이 완료되면 조회되지 않는다")
        void completed() {
            Settlement settlement = SettlementReminderRepositoryTest.this.requested(
                    taxiPot(host, CompanionStatus.COMPLETED, 3));
            settlement.complete();
            entityManager.flush();

            assertThat(reminderRepository.findRequestedForUpdate(settlement.getId())).isEmpty();
        }
    }

    @Test
    @DisplayName("findPendingParticipants: 나가거나 완주한 참여자는 제외한다")
    void pendingParticipants() {
        Companion pot = entityManager.persist(taxiPot(host, CompanionStatus.COMPLETED, 3));
        User rider = entityManager.persist(bankAccountHolder("동승자"));
        User leaver = entityManager.persist(bankAccountHolder("나간사람"));
        User finisher = entityManager.persist(bankAccountHolder("완주자"));
        entityManager.persist(participant(pot, host, PENDING));
        entityManager.persist(participant(pot, rider, PENDING));
        entityManager.persist(participant(pot, leaver, INCOMPLETE));
        entityManager.persist(participant(pot, finisher, COMPLETED));
        entityManager.flush();

        assertThat(reminderRepository.findPendingParticipants(pot.getId()))
                .extracting(participant -> participant.getUser().getId())
                .containsExactly(host.getId(), rider.getId());
    }

    @Test
    @DisplayName("findChatRoom: 택시팟의 채팅방을 조회한다")
    void chatRoom() {
        Companion pot = entityManager.persist(taxiPot(host, CompanionStatus.COMPLETED, 3));
        ChatRoom chatRoom = entityManager.persist(ChatRoom.create(pot));
        entityManager.flush();

        assertThat(reminderRepository.findChatRoom(pot.getId())).contains(chatRoom);
    }

    private Settlement settlement(Companion companion) {
        entityManager.persist(companion);
        return entityManager.persist(Settlement.open(companion));
    }

    private Settlement requested(Companion companion) {
        Settlement settlement = settlement(companion);
        settlement.requestManually(9000);
        return settlement;
    }
}
