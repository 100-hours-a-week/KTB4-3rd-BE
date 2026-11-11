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
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
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
    @DisplayName("markPublished (발행 ack 기록)")
    class MarkPublished {

        @Test
        @DisplayName("PENDING 작업만 PUBLISHED 로 바꾼다")
        void pendingOnly() {
            NotificationPushJob pending = persistJob(PushJobStatus.PENDING, NOW);
            NotificationPushJob sent = persistJob(PushJobStatus.SENT, NOW);

            int marked = jobRepository.markPublished(List.of(pending.getId(), sent.getId()), LEASE_UNTIL);

            assertThat(marked).isEqualTo(1);
            assertThat(reload(pending).getStatus()).isEqualTo(PushJobStatus.PUBLISHED);
            assertThat(reload(pending).getUpdatedAt()).isEqualTo(LEASE_UNTIL);
            assertThat(reload(sent).getStatus()).isEqualTo(PushJobStatus.SENT);
        }

        @Test
        @DisplayName("ack 가 늦게 와도 컨슈머가 먼저 바꾼 SENDING 을 되돌리지 않는다")
        void lateAck() {
            NotificationPushJob sending = persistJob(PushJobStatus.SENDING, LEASE_UNTIL);

            int marked = jobRepository.markPublished(List.of(sending.getId()), NOW);

            assertThat(marked).isZero();
            assertThat(reload(sending).getStatus()).isEqualTo(PushJobStatus.SENDING);
        }
    }

    @Nested
    @DisplayName("requeueAll (다시 발행할 작업 되돌리기)")
    class RequeueAll {

        @Test
        @DisplayName("PENDING 으로 두고 다음 시도 시각을 지금으로 미룬다")
        void requeue() {
            NotificationPushJob sending = persistJob(PushJobStatus.SENDING, NOW.minusMinutes(5));

            jobRepository.requeueAll(List.of(sending.getId()), NOW);
            NotificationPushJob found = reload(sending);

            assertThat(found.getStatus()).isEqualTo(PushJobStatus.PENDING);
            assertThat(found.getNextAttemptAt()).isEqualTo(NOW);
            assertThat(found.getUpdatedAt()).isEqualTo(NOW);
        }
    }

    @Nested
    @DisplayName("findTypesByIdIn (다시 발행할 토픽 고르기)")
    class FindTypes {

        @Test
        @DisplayName("작업 id 와 알림 타입을 함께 읽는다")
        void readsTypes() {
            NotificationPushJob job = persistJob(PushJobStatus.PENDING, NOW);

            List<PushJobTypeProjection> rows = jobRepository.findTypesByIdIn(List.of(job.getId()));

            assertThat(rows).singleElement().satisfies(row -> {
                assertThat(row.getId()).isEqualTo(job.getId());
                assertThat(row.getType()).isEqualTo(NotificationType.MATCHING_COMPLETED);
            });
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

    @Nested
    @DisplayName("claimRequested (컨슈머 발송 선점)")
    class ClaimRequested {

        @ParameterizedTest
        @EnumSource(value = PushJobStatus.class, names = {"PENDING", "PUBLISHED"})
        @DisplayName("대기 중이거나 발행된 작업을 SENDING으로 바꾸고 시도 횟수를 1 올린다")
        void claim(PushJobStatus status) {
            NotificationPushJob job = persistJob(status, NOW);

            int claimed = jobRepository.claimRequested(job.getId(), NOW, LEASE_UNTIL);
            NotificationPushJob found = reload(job);

            assertThat(claimed).isEqualTo(1);
            assertThat(found.getStatus()).isEqualTo(PushJobStatus.SENDING);
            assertThat(found.getAttempts()).isEqualTo(1);
            assertThat(found.getNextAttemptAt()).isEqualTo(LEASE_UNTIL);
            assertThat(found.getUpdatedAt()).isEqualTo(NOW);
        }

        @Test
        @DisplayName("같은 메시지를 두 번 받아도 두 번째는 0을 반환한다")
        void onlyOnce() {
            NotificationPushJob job = persistJob(PushJobStatus.PUBLISHED, NOW);

            int first = jobRepository.claimRequested(job.getId(), NOW, LEASE_UNTIL);
            int second = jobRepository.claimRequested(job.getId(), NOW, LEASE_UNTIL);

            assertThat(first).isEqualTo(1);
            assertThat(second).isZero();
            assertThat(reload(job).getAttempts()).isEqualTo(1);
        }

        @ParameterizedTest
        @EnumSource(value = PushJobStatus.class, names = {"SENDING", "SENT", "FAILED", "SKIPPED"})
        @DisplayName("보내는 중이거나 끝난 작업은 선점하지 않는다")
        void notRequested(PushJobStatus status) {
            NotificationPushJob job = persistJob(status, NOW);

            assertThat(jobRepository.claimRequested(job.getId(), NOW, LEASE_UNTIL)).isZero();
            assertThat(reload(job).getStatus()).isEqualTo(status);
        }
    }

    @Nested
    @DisplayName("failExhausted (스윕의 시도 상한 실패 처리)")
    class FailExhausted {

        private static final int MAX_ATTEMPTS = 5;

        @Test
        @DisplayName("시도 횟수가 상한에 닿은 작업만 FAILED 로 바꾸고 사유를 남긴다")
        void failsOnlyExhaustedJobs() {
            NotificationPushJob exhausted = persistJob(PushJobStatus.SENDING, NOW);
            ReflectionTestUtils.setField(exhausted, "attempts", MAX_ATTEMPTS);
            persist(exhausted);
            NotificationPushJob belowLimit = persistJob(PushJobStatus.SENDING, NOW);
            ReflectionTestUtils.setField(belowLimit, "attempts", MAX_ATTEMPTS - 1);
            persist(belowLimit);

            int failed = jobRepository.failExhausted(
                    List.of(exhausted.getId(), belowLimit.getId()), MAX_ATTEMPTS, NOW, "SWEEP_ATTEMPTS_EXHAUSTED");

            assertThat(failed).isEqualTo(1);
            NotificationPushJob found = reload(exhausted);
            assertThat(found.getStatus()).isEqualTo(PushJobStatus.FAILED);
            assertThat(found.getLastError()).isEqualTo("SWEEP_ATTEMPTS_EXHAUSTED");
            assertThat(reload(belowLimit).getStatus()).isEqualTo(PushJobStatus.SENDING);
        }

        @Test
        @DisplayName("이미 끝난 작업은 시도 횟수가 넘어도 바꾸지 않는다")
        void keepsFinishedJobs() {
            NotificationPushJob sent = persistJob(PushJobStatus.SENT, NOW);
            ReflectionTestUtils.setField(sent, "attempts", MAX_ATTEMPTS);
            persist(sent);

            int failed = jobRepository.failExhausted(List.of(sent.getId()), MAX_ATTEMPTS, NOW, "SWEEP_ATTEMPTS_EXHAUSTED");

            assertThat(failed).isZero();
            assertThat(reload(sent).getStatus()).isEqualTo(PushJobStatus.SENT);
        }
    }

    private NotificationPushJob persistJob(PushJobStatus status, LocalDateTime nextAttemptAt) {
        NotificationPushJob job = NotificationPushJob.create(persist(notification()), NOW);
        ReflectionTestUtils.setField(job, "status", status);
        ReflectionTestUtils.setField(job, "nextAttemptAt", nextAttemptAt);
        return persist(job);
    }

    private long nextEventId = 1L;

    private Notification notification() {
        return Notification.createForChatRoom(
                recipient, NotificationType.MATCHING_COMPLETED, nextEventId++, chatRoom, "매칭이 완료됐어요");
    }

    private NotificationPushJob reload(NotificationPushJob job) {
        entityManager.clear();
        return entityManager.find(NotificationPushJob.class, job.getId());
    }

    private <T> T persist(T entity) {
        return entityManager.persistAndFlush(entity);
    }
}
