package com.ktb.moyeota.domain.notification.service;

import static com.ktb.moyeota.domain.chat.entity.OutcomeStatus.INCOMPLETE;
import static com.ktb.moyeota.domain.chat.entity.OutcomeStatus.PENDING;
import static com.ktb.moyeota.fixture.CompanionFixture.taxiPot;
import static com.ktb.moyeota.fixture.ParticipantFixture.participant;
import static com.ktb.moyeota.fixture.UserFixture.bankAccountHolder;
import static org.assertj.core.api.Assertions.assertThat;

import com.ktb.moyeota.domain.chat.entity.ChatRoom;
import com.ktb.moyeota.domain.companion.entity.Companion;
import com.ktb.moyeota.domain.companion.entity.CompanionStatus;
import com.ktb.moyeota.domain.notification.entity.Notification;
import com.ktb.moyeota.domain.notification.entity.NotificationPushJob;
import com.ktb.moyeota.domain.notification.entity.NotificationType;
import com.ktb.moyeota.domain.notification.entity.PushJobStatus;
import com.ktb.moyeota.domain.notification.event.NotificationBulkPushRequestedEvent;
import com.ktb.moyeota.domain.notification.repository.NotificationPushJobRepository;
import com.ktb.moyeota.domain.notification.repository.NotificationRepository;
import com.ktb.moyeota.domain.settlement.entity.Settlement;
import com.ktb.moyeota.domain.user.entity.User;
import com.ktb.moyeota.global.config.MutableClock;
import com.ktb.moyeota.global.config.TestClockConfig;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;
import org.springframework.test.util.ReflectionTestUtils;

@DataJpaTest
@Import({SettlementReminderService.class, TestClockConfig.class})
@RecordApplicationEvents
class SettlementReminderServiceTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 28, 21, 0, 0);
    private static final LocalDateTime ROUND = LocalDateTime.of(2026, 10, 28, 21, 0, 0);
    private static final LocalDateTime PREVIOUS_ROUND = LocalDateTime.of(2026, 10, 28, 9, 0, 0);

    @Autowired
    private MutableClock clock;

    @Autowired
    private SettlementReminderService reminderService;

    @Autowired
    private NotificationRepository notificationRepository;

    @Autowired
    private NotificationPushJobRepository jobRepository;

    @Autowired
    private TestEntityManager entityManager;

    @Autowired
    private ApplicationEvents events;

    private User host;
    private User rider;
    private ChatRoom chatRoom;
    private Settlement settlement;

    @BeforeEach
    void setUp() {
        clock.set(NOW);
        host = entityManager.persist(bankAccountHolder("방장"));
        rider = entityManager.persist(bankAccountHolder("동승자"));
        User leaver = entityManager.persist(bankAccountHolder("나간사람"));
        Companion pot = entityManager.persist(taxiPot(host, CompanionStatus.COMPLETED, 2));
        chatRoom = entityManager.persist(ChatRoom.create(pot));
        entityManager.persist(participant(pot, host, PENDING));
        entityManager.persist(participant(pot, rider, PENDING));
        entityManager.persist(participant(pot, leaver, INCOMPLETE));
        settlement = entityManager.persist(Settlement.open(pot));
        settlement.requestManually(9000);
        entityManager.flush();
        entityManager.clear();
    }

    @Test
    @DisplayName("처음 보낼 때 방장과 동승자에게 역할별 문구로 알림과 PENDING 발송 작업을 만든다")
    void createsForHostAndRider() {
        reminderService.remind(settlement.getId(), ROUND);
        entityManager.flush();
        entityManager.clear();

        Notification hostNotification = reminderOf(host);
        Notification riderNotification = reminderOf(rider);
        assertThat(notificationRepository.findAll()).hasSize(2);
        assertThat(hostNotification.getContent()).isEqualTo(SettlementReminderService.HOST_CONTENT);
        assertThat(riderNotification.getContent()).isEqualTo(SettlementReminderService.RIDER_CONTENT);
        assertThat(hostNotification.getTargetChatRoom().getId()).isEqualTo(chatRoom.getId());
        assertThat(hostNotification.getCreatedAt()).isEqualTo(NOW);
        assertThat(jobOf(hostNotification).getStatus()).isEqualTo(PushJobStatus.PENDING);
        assertThat(jobOf(riderNotification).getStatus()).isEqualTo(PushJobStatus.PENDING);
    }

    @ParameterizedTest
    @EnumSource(value = PushJobStatus.class, names = {"SENT", "FAILED", "SKIPPED"})
    @DisplayName("다음 회차에는 기존 알림을 다시 쓰고 끝난 발송 작업을 PENDING으로 되돌린다")
    void reusesExistingRows(PushJobStatus finished) {
        Notification previous = previousReminder(host, finished);

        reminderService.remind(settlement.getId(), ROUND);
        entityManager.flush();
        entityManager.clear();

        Notification reminded = reminderOf(host);
        NotificationPushJob job = jobOf(reminded);
        assertThat(reminded.getId()).isEqualTo(previous.getId());
        assertThat(reminded.getCreatedAt()).isEqualTo(NOW);
        assertThat(reminded.getReadAt()).isNull();
        assertThat(job.getStatus()).isEqualTo(PushJobStatus.PENDING);
        assertThat(job.getAttempts()).isZero();
        assertThat(job.getNextAttemptAt()).isEqualTo(NOW);
        assertThat(jobRepository.findAll()).hasSize(2);
    }

    @ParameterizedTest
    @EnumSource(value = PushJobStatus.class, names = {"PENDING", "PUBLISHED", "SENDING"})
    @DisplayName("이전 회차 발송 작업이 아직 대기 중이거나 발행됐거나 보내는 중이면 그대로 둔다")
    void keepsInFlightJob(PushJobStatus inFlight) {
        previousReminder(host, inFlight);

        reminderService.remind(settlement.getId(), ROUND);
        entityManager.flush();
        entityManager.clear();

        NotificationPushJob job = jobOf(reminderOf(host));
        assertThat(job.getStatus()).isEqualTo(inFlight);
        assertThat(job.getAttempts()).isEqualTo(1);
        assertThat(job.getNextAttemptAt()).isEqualTo(PREVIOUS_ROUND);
    }

    @Test
    @DisplayName("다른 서버가 이번 회차를 이미 처리했으면 건너뛴다")
    void skipsSameRound() {
        reminderService.remind(settlement.getId(), ROUND);
        entityManager.flush();
        entityManager.clear();
        NotificationPushJob first = jobOf(reminderOf(host));
        ReflectionTestUtils.setField(first, "status", PushJobStatus.SENT);
        entityManager.merge(first);
        entityManager.flush();
        entityManager.clear();

        reminderService.remind(settlement.getId(), ROUND);
        entityManager.flush();
        entityManager.clear();

        assertThat(notificationRepository.findAll()).hasSize(2);
        assertThat(jobRepository.findAll()).hasSize(2);
        assertThat(jobOf(reminderOf(host)).getStatus()).isEqualTo(PushJobStatus.SENT);
    }

    @Test
    @DisplayName("새로 만든 발송 작업 id를 정산 하나당 한 번의 전체 발송 요청 이벤트로 알린다")
    void publishesCreatedJobs() {
        reminderService.remind(settlement.getId(), ROUND);
        entityManager.flush();
        entityManager.clear();

        assertThat(bulkPushRequests()).containsExactly(
                new NotificationBulkPushRequestedEvent(List.of(jobOf(reminderOf(host)).getId(),
                        jobOf(reminderOf(rider)).getId())));
    }

    @ParameterizedTest
    @EnumSource(value = PushJobStatus.class, names = {"SENT", "FAILED", "SKIPPED"})
    @DisplayName("다시 연 발송 작업도 전체 발송 요청 이벤트에 넣는다")
    void publishesReopenedJob(PushJobStatus finished) {
        Notification previous = previousReminder(host, finished);

        reminderService.remind(settlement.getId(), ROUND);
        entityManager.flush();
        entityManager.clear();

        assertThat(bulkPushRequests()).singleElement()
                .satisfies(event -> assertThat(event.jobIds()).contains(jobOf(previous).getId()));
    }

    @ParameterizedTest
    @EnumSource(value = PushJobStatus.class, names = {"PENDING", "PUBLISHED", "SENDING"})
    @DisplayName("이미 발행 중인 이전 회차 작업은 다시 발행하지 않고 새 작업만 알린다")
    void skipsInFlightJob(PushJobStatus inFlight) {
        previousReminder(host, inFlight);

        reminderService.remind(settlement.getId(), ROUND);
        entityManager.flush();
        entityManager.clear();

        assertThat(bulkPushRequests()).containsExactly(
                new NotificationBulkPushRequestedEvent(List.of(jobOf(reminderOf(rider)).getId())));
    }

    @Test
    @DisplayName("이번 회차를 이미 처리했으면 전체 발송 요청 이벤트를 내지 않는다")
    void noEventForSameRound() {
        reminderService.remind(settlement.getId(), ROUND);
        entityManager.flush();
        entityManager.clear();
        events.clear();

        reminderService.remind(settlement.getId(), ROUND);
        entityManager.flush();

        assertThat(bulkPushRequests()).isEmpty();
    }

    @Test
    @DisplayName("정산이 완료됐으면 보내지 않는다")
    void skipsCompletedSettlement() {
        Settlement completed = entityManager.find(Settlement.class, settlement.getId());
        completed.complete();
        entityManager.flush();
        entityManager.clear();

        reminderService.remind(settlement.getId(), ROUND);
        entityManager.flush();

        assertThat(notificationRepository.findAll()).isEmpty();
        assertThat(jobRepository.findAll()).isEmpty();
        assertThat(bulkPushRequests()).isEmpty();
    }

    @Test
    @DisplayName("findSettlementIdsToRemind: REQUESTED로 남은 정산 id를 돌려준다")
    void findSettlementIds() {
        assertThat(reminderService.findSettlementIdsToRemind()).containsExactly(settlement.getId());
    }

    private Notification previousReminder(User recipient, PushJobStatus status) {
        Notification notification = entityManager.persist(Notification.createSettlementReminder(
                entityManager.find(User.class, recipient.getId()), settlement.getId(),
                entityManager.find(ChatRoom.class, chatRoom.getId()), SettlementReminderService.HOST_CONTENT,
                PREVIOUS_ROUND));
        ReflectionTestUtils.setField(notification, "readAt", PREVIOUS_ROUND.plusMinutes(5));
        NotificationPushJob job = NotificationPushJob.create(notification, PREVIOUS_ROUND);
        ReflectionTestUtils.setField(job, "status", status);
        ReflectionTestUtils.setField(job, "attempts", 1);
        entityManager.persist(job);
        entityManager.flush();
        entityManager.clear();
        return notification;
    }

    private Notification reminderOf(User recipient) {
        return notificationRepository.findByRecipientIdAndTypeAndEventId(
                recipient.getId(), NotificationType.SETTLEMENT_COMPLETION_REMINDER, settlement.getId()).orElseThrow();
    }

    private NotificationPushJob jobOf(Notification notification) {
        return jobRepository.findByNotificationId(notification.getId()).orElseThrow();
    }

    private List<NotificationBulkPushRequestedEvent> bulkPushRequests() {
        return events.stream(NotificationBulkPushRequestedEvent.class).toList();
    }
}
