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
import com.ktb.moyeota.domain.notification.entity.Notification;
import com.ktb.moyeota.domain.notification.entity.NotificationPushJob;
import com.ktb.moyeota.domain.settlement.entity.Settlement;
import com.ktb.moyeota.domain.user.entity.User;
import java.time.LocalDateTime;
import java.util.List;
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

    @Nested
    @DisplayName("findReminders")
    class FindReminders {

        private static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 28, 9, 0);

        private ChatRoom chatRoom;
        private Settlement settlement;

        @BeforeEach
        void setUp() {
            Companion pot = entityManager.persist(taxiPot(host, CompanionStatus.COMPLETED, 3));
            chatRoom = entityManager.persist(ChatRoom.create(pot));
            settlement = entityManager.persist(Settlement.open(pot));
        }

        @Test
        @DisplayName("이 정산의 리마인드 알림과 발송 작업을 함께 돌려준다")
        void withJob() {
            Notification notification = reminder(host, settlement.getId());
            NotificationPushJob job = entityManager.persist(NotificationPushJob.create(notification, NOW));
            entityManager.flush();
            entityManager.clear();

            assertThat(reminderRepository.findReminders(List.of(host.getId()), settlement.getId()))
                    .singleElement()
                    .satisfies(found -> {
                        assertThat(found.notification().getId()).isEqualTo(notification.getId());
                        assertThat(found.job().getId()).isEqualTo(job.getId());
                    });
        }

        @Test
        @DisplayName("발송 작업이 없으면 알림만 돌려준다")
        void withoutJob() {
            Notification notification = reminder(host, settlement.getId());
            entityManager.flush();
            entityManager.clear();

            assertThat(reminderRepository.findReminders(List.of(host.getId()), settlement.getId()))
                    .singleElement()
                    .satisfies(found -> {
                        assertThat(found.notification().getId()).isEqualTo(notification.getId());
                        assertThat(found.job()).isNull();
                    });
        }

        @Test
        @DisplayName("받을 사람 여럿의 알림을 한 번에 돌려주고, 목록에 없는 사람과 다른 정산의 알림은 뺀다")
        void onlyMatching() {
            User rider = entityManager.persist(bankAccountHolder("동승자"));
            User other = entityManager.persist(bankAccountHolder("다른사람"));
            Notification hostReminder = reminder(host, settlement.getId());
            Notification riderReminder = reminder(rider, settlement.getId());
            reminder(other, settlement.getId());
            reminder(host, settlement.getId() + 1);
            entityManager.flush();
            entityManager.clear();

            assertThat(reminderRepository.findReminders(List.of(host.getId(), rider.getId()), settlement.getId()))
                    .extracting(found -> found.notification().getId())
                    .containsExactlyInAnyOrder(hostReminder.getId(), riderReminder.getId());
        }

        private Notification reminder(User recipient, Long settlementId) {
            return entityManager.persist(Notification.createSettlementReminder(
                    recipient, settlementId, chatRoom, "정산을 마쳐 주세요", NOW));
        }
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
