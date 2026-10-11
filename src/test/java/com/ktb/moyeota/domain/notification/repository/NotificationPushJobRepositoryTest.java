package com.ktb.moyeota.domain.notification.repository;

import static com.ktb.moyeota.fixture.CompanionFixture.companionPost;
import static com.ktb.moyeota.fixture.UserFixture.user;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ktb.moyeota.domain.chat.entity.ChatRoom;
import com.ktb.moyeota.domain.companion.entity.Companion;
import com.ktb.moyeota.domain.notification.entity.Notification;
import com.ktb.moyeota.domain.notification.entity.NotificationPushJob;
import com.ktb.moyeota.domain.notification.entity.NotificationType;
import com.ktb.moyeota.domain.notification.entity.PushJobStatus;
import com.ktb.moyeota.domain.user.entity.User;
import jakarta.persistence.PersistenceException;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.util.ReflectionTestUtils;

@DataJpaTest
class NotificationPushJobRepositoryTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 12, 9, 0, 0);
    private static final LocalDateTime LEASE_UNTIL = NOW.plusSeconds(60);
    private static final List<PushJobStatus> CLAIMABLE = List.of(PushJobStatus.PENDING, PushJobStatus.SENDING);

    @Autowired
    private NotificationPushJobRepository jobRepository;

    @Autowired
    private TestEntityManager entityManager;

    private User recipient;
    private ChatRoom chatRoom;

    @BeforeEach
    void setUp() {
        recipient = persist(user("수신자"));
        Companion companion = persist(companionPost(recipient));
        chatRoom = persist(ChatRoom.create(companion));
    }

    @Nested
    @DisplayName("claimPending (즉시 발송 선점)")
    class ClaimPending {

        @Test
        @DisplayName("PENDING 작업을 SENDING으로 바꾸고 시도 횟수를 1 올린다")
        void claim() {
            NotificationPushJob job = persistJob(PushJobStatus.PENDING, NOW);

            int claimed = jobRepository.claimPending(job.getId(), NOW, LEASE_UNTIL);
            NotificationPushJob found = reload(job);

            assertThat(claimed).isEqualTo(1);
            assertThat(found.getStatus()).isEqualTo(PushJobStatus.SENDING);
            assertThat(found.getAttempts()).isEqualTo(1);
            assertThat(found.getNextAttemptAt()).isEqualTo(LEASE_UNTIL);
            assertThat(found.getUpdatedAt()).isEqualTo(NOW);
        }

        @Test
        @DisplayName("같은 작업을 두 번 선점하면 두 번째는 0을 반환한다")
        void onlyOnce() {
            NotificationPushJob job = persistJob(PushJobStatus.PENDING, NOW);

            int first = jobRepository.claimPending(job.getId(), NOW, LEASE_UNTIL);
            int second = jobRepository.claimPending(job.getId(), NOW, LEASE_UNTIL);

            assertThat(first).isEqualTo(1);
            assertThat(second).isZero();
            assertThat(reload(job).getAttempts()).isEqualTo(1);
        }

        @Test
        @DisplayName("PENDING이 아닌 작업은 선점하지 않는다")
        void notPending() {
            NotificationPushJob sending = persistJob(PushJobStatus.SENDING, LEASE_UNTIL);
            NotificationPushJob sent = persistJob(PushJobStatus.SENT, NOW);
            NotificationPushJob failed = persistJob(PushJobStatus.FAILED, NOW);
            NotificationPushJob skipped = persistJob(PushJobStatus.SKIPPED, NOW);

            assertThat(jobRepository.claimPending(sending.getId(), NOW, LEASE_UNTIL)).isZero();
            assertThat(jobRepository.claimPending(sent.getId(), NOW, LEASE_UNTIL)).isZero();
            assertThat(jobRepository.claimPending(failed.getId(), NOW, LEASE_UNTIL)).isZero();
            assertThat(jobRepository.claimPending(skipped.getId(), NOW, LEASE_UNTIL)).isZero();
        }
    }

    @Nested
    @DisplayName("findClaimableForUpdate (스윕 대상 조회)")
    class FindClaimable {

        @Test
        @DisplayName("재시도 시각이 된 PENDING과 선점 만료가 지난 SENDING을 id 순으로 가져온다")
        void dueJobs() {
            NotificationPushJob duePending = persistJob(PushJobStatus.PENDING, NOW.minusSeconds(1));
            NotificationPushJob expiredSending = persistJob(PushJobStatus.SENDING, NOW.minusSeconds(1));
            NotificationPushJob pendingNow = persistJob(PushJobStatus.PENDING, NOW);

            List<NotificationPushJob> result = jobRepository.findClaimableForUpdate(
                    CLAIMABLE, NOW, PageRequest.of(0, 50));

            assertThat(result).extracting(NotificationPushJob::getId)
                    .containsExactly(duePending.getId(), expiredSending.getId(), pendingNow.getId());
        }

        @Test
        @DisplayName("재시도 시각 전인 PENDING과 선점 중인 SENDING은 가져오지 않는다")
        void notYetDue() {
            persistJob(PushJobStatus.PENDING, NOW.plusSeconds(30));
            persistJob(PushJobStatus.SENDING, LEASE_UNTIL);

            assertThat(jobRepository.findClaimableForUpdate(CLAIMABLE, NOW, PageRequest.of(0, 50))).isEmpty();
        }

        @Test
        @DisplayName("SENT, FAILED, SKIPPED는 시각과 관계없이 가져오지 않는다")
        void finished() {
            persistJob(PushJobStatus.SENT, NOW.minusDays(1));
            persistJob(PushJobStatus.FAILED, NOW.minusDays(1));
            persistJob(PushJobStatus.SKIPPED, NOW.minusDays(1));

            assertThat(jobRepository.findClaimableForUpdate(CLAIMABLE, NOW, PageRequest.of(0, 50))).isEmpty();
        }

        @Test
        @DisplayName("한 번에 가져오는 개수를 제한한다")
        void limit() {
            for (int i = 0; i < 5; i++) {
                persistJob(PushJobStatus.PENDING, NOW);
            }

            assertThat(jobRepository.findClaimableForUpdate(CLAIMABLE, NOW, PageRequest.of(0, 3))).hasSize(3);
        }
    }

    @Nested
    @DisplayName("claimAll (스윕 일괄 선점)")
    class ClaimAll {

        @Test
        @DisplayName("지정한 작업들을 SENDING으로 바꾸고 시도 횟수를 1씩 올린다")
        void claimAll() {
            NotificationPushJob pending = persistJob(PushJobStatus.PENDING, NOW);
            NotificationPushJob expired = persistJob(PushJobStatus.SENDING, NOW.minusSeconds(1));
            ReflectionTestUtils.setField(expired, "attempts", 1);
            entityManager.flush();

            int claimed = jobRepository.claimAll(List.of(pending.getId(), expired.getId()), NOW, LEASE_UNTIL);

            assertThat(claimed).isEqualTo(2);
            NotificationPushJob foundPending = reload(pending);
            NotificationPushJob foundExpired = reload(expired);
            assertThat(foundPending.getStatus()).isEqualTo(PushJobStatus.SENDING);
            assertThat(foundPending.getAttempts()).isEqualTo(1);
            assertThat(foundPending.getNextAttemptAt()).isEqualTo(LEASE_UNTIL);
            assertThat(foundExpired.getStatus()).isEqualTo(PushJobStatus.SENDING);
            assertThat(foundExpired.getAttempts()).isEqualTo(2);
        }

        @Test
        @DisplayName("지정하지 않은 작업은 바꾸지 않는다")
        void onlyGivenIds() {
            NotificationPushJob target = persistJob(PushJobStatus.PENDING, NOW);
            NotificationPushJob other = persistJob(PushJobStatus.PENDING, NOW);

            jobRepository.claimAll(List.of(target.getId()), NOW, LEASE_UNTIL);

            assertThat(reload(other).getStatus()).isEqualTo(PushJobStatus.PENDING);
            assertThat(reload(other).getAttempts()).isZero();
        }
    }

    @Nested
    @DisplayName("제약 조건")
    class Constraints {

        @Test
        @DisplayName("같은 알림으로 작업을 두 번 만들 수 없다 (UNIQUE notification_id)")
        void uniqueNotification() {
            Notification notification = persist(notification());
            persist(NotificationPushJob.create(notification, NOW));

            assertThatThrownBy(() -> persist(NotificationPushJob.create(notification, NOW)))
                    .isInstanceOf(PersistenceException.class);
        }

        @Test
        @DisplayName("알림이 삭제되면 작업도 함께 삭제된다 (ON DELETE CASCADE)")
        void cascadeDelete() {
            Notification notification = persist(notification());
            NotificationPushJob job = persist(NotificationPushJob.create(notification, NOW));
            entityManager.clear();

            entityManager.getEntityManager()
                    .createNativeQuery("DELETE FROM notifications WHERE id = :id")
                    .setParameter("id", notification.getId())
                    .executeUpdate();
            entityManager.clear();

            assertThat(entityManager.find(NotificationPushJob.class, job.getId())).isNull();
        }
    }

    private NotificationPushJob persistJob(PushJobStatus status, LocalDateTime nextAttemptAt) {
        NotificationPushJob job = NotificationPushJob.create(persist(notification()), NOW);
        ReflectionTestUtils.setField(job, "status", status);
        ReflectionTestUtils.setField(job, "nextAttemptAt", nextAttemptAt);
        return persist(job);
    }

    private Notification notification() {
        return Notification.createForChatRoom(
                recipient, NotificationType.MATCHING_COMPLETED, 1L, chatRoom, "매칭이 완료됐어요");
    }

    private NotificationPushJob reload(NotificationPushJob job) {
        entityManager.clear();
        return entityManager.find(NotificationPushJob.class, job.getId());
    }

    private <T> T persist(T entity) {
        return entityManager.persistAndFlush(entity);
    }
}
