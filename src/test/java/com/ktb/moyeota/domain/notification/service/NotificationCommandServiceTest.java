package com.ktb.moyeota.domain.notification.service;

import static com.ktb.moyeota.fixture.CompanionFixture.companionPost;
import static com.ktb.moyeota.fixture.UserFixture.user;
import static org.assertj.core.api.Assertions.assertThat;

import com.ktb.moyeota.domain.chat.entity.ChatRoom;
import com.ktb.moyeota.domain.community.entity.CommunityPost;
import com.ktb.moyeota.domain.companion.entity.Companion;
import com.ktb.moyeota.domain.notification.entity.Notification;
import com.ktb.moyeota.domain.notification.entity.NotificationPushJob;
import com.ktb.moyeota.domain.notification.entity.NotificationType;
import com.ktb.moyeota.domain.notification.entity.PushJobStatus;
import com.ktb.moyeota.domain.notification.event.NotificationPushRequestedEvent;
import com.ktb.moyeota.domain.notification.repository.NotificationPushJobRepository;
import com.ktb.moyeota.domain.user.entity.User;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;

@DataJpaTest
@RecordApplicationEvents
@Import({NotificationCommandService.class, NotificationCommandServiceTest.FixedClockConfig.class})
class NotificationCommandServiceTest {

    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 12, 9, 0, 0);

    @TestConfiguration
    static class FixedClockConfig {

        @Bean
        Clock clock() {
            return Clock.fixed(NOW.atZone(SEOUL).toInstant(), SEOUL);
        }
    }

    @Autowired
    private NotificationCommandService commandService;

    @Autowired
    private NotificationPushJobRepository jobRepository;

    @Autowired
    private TestEntityManager entityManager;

    @Autowired
    private ApplicationEvents events;

    private User recipient;
    private ChatRoom chatRoom;
    private CommunityPost post;

    @BeforeEach
    void setUp() {
        recipient = entityManager.persist(user("수신자"));
        Companion companion = entityManager.persist(companionPost(recipient));
        chatRoom = entityManager.persist(ChatRoom.create(companion));
        post = entityManager.persist(CommunityPost.create(recipient, "판교역 혼잡", "사람 많아요",
                new BigDecimal("37.394500"), new BigDecimal("127.111200")));
        entityManager.flush();
    }

    @Test
    @DisplayName("채팅방 알림을 저장하면 같은 알림의 발송 작업이 PENDING으로 함께 저장된다")
    void chatRoomNotification() {
        Notification notification = commandService.createForChatRoom(
                recipient, NotificationType.CARPOOL_REQUEST_RECEIVED, 31L, chatRoom, "민준님이 동승을 요청했어요");

        NotificationPushJob job = onlyJob();
        assertThat(job.getNotification().getId()).isEqualTo(notification.getId());
        assertThat(job.getStatus()).isEqualTo(PushJobStatus.PENDING);
        assertThat(job.getAttempts()).isZero();
        assertThat(job.getNextAttemptAt()).isEqualTo(NOW);
    }

    @Test
    @DisplayName("댓글 알림도 발송 작업이 함께 저장된다 (모든 타입 푸시 대상)")
    void commentNotification() {
        Notification notification = commandService.createForComment(recipient, 12L, post, "우림님이 댓글을 남겼어요");

        NotificationPushJob job = onlyJob();
        assertThat(job.getNotification().getId()).isEqualTo(notification.getId());
        assertThat(job.getStatus()).isEqualTo(PushJobStatus.PENDING);
    }

    @Test
    @DisplayName("저장한 발송 작업 id로 즉시 발송 이벤트를 발행한다")
    void publishesEvent() {
        commandService.createForChatRoom(
                recipient, NotificationType.MATCHING_COMPLETED, 40L, chatRoom, "매칭이 완료됐어요");

        List<NotificationPushRequestedEvent> published =
                events.stream(NotificationPushRequestedEvent.class).toList();
        assertThat(published).hasSize(1);
        assertThat(published.getFirst().jobId()).isEqualTo(onlyJob().getId());
    }

    private NotificationPushJob onlyJob() {
        entityManager.flush();
        entityManager.clear();
        List<NotificationPushJob> jobs = jobRepository.findAll();
        assertThat(jobs).hasSize(1);
        return jobRepository.findWithNotificationById(jobs.getFirst().getId()).orElseThrow();
    }
}
