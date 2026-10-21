package com.ktb.moyeota.domain.notification.service;

import static com.ktb.moyeota.fixture.CompanionFixture.companionPost;
import static com.ktb.moyeota.fixture.UserFixture.user;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.ktb.moyeota.domain.chat.entity.ChatRoom;
import com.ktb.moyeota.domain.companion.entity.Companion;
import com.ktb.moyeota.domain.notification.entity.Notification;
import com.ktb.moyeota.domain.notification.entity.NotificationPushJob;
import com.ktb.moyeota.domain.notification.entity.NotificationType;
import com.ktb.moyeota.domain.notification.entity.PushJobStatus;
import com.ktb.moyeota.domain.notification.push.PushMessage;
import com.ktb.moyeota.domain.notification.push.PushSendResult;
import com.ktb.moyeota.domain.notification.push.PushSender;
import com.ktb.moyeota.domain.notification.push.TokenSendOutcome;
import com.ktb.moyeota.domain.notification.push.TokenSendResult;
import com.ktb.moyeota.domain.user.entity.User;
import com.ktb.moyeota.domain.user.repository.PushTokenRepository;
import com.ktb.moyeota.global.config.NotificationPushProperties;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.util.ReflectionTestUtils;

@DataJpaTest
@Import({NotificationPushService.class, NotificationPushServiceTest.TestConfig.class})
class NotificationPushServiceTest {

    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 12, 9, 0, 0);
    private static final LocalDateTime LEASE_UNTIL = NOW.plusSeconds(60);
    private static final LocalDateTime RETRY_AT = NOW.plusSeconds(30);

    @TestConfiguration
    @EnableConfigurationProperties(NotificationPushProperties.class)
    static class TestConfig {

        @Bean
        Clock clock() {
            return Clock.fixed(NOW.atZone(SEOUL).toInstant(), SEOUL);
        }

        @Bean
        StubPushSender pushSender() {
            return new StubPushSender();
        }
    }

    static class StubPushSender implements PushSender {

        private PushSendResult nextResult = new PushSendResult(List.of());
        private final List<List<String>> sentTokens = new ArrayList<>();
        private PushMessage lastMessage;

        void reset() {
            this.nextResult = new PushSendResult(List.of());
            this.sentTokens.clear();
            this.lastMessage = null;
        }

        void willReturn(TokenSendResult... results) {
            this.nextResult = new PushSendResult(List.of(results));
        }

        @Override
        public PushSendResult send(List<String> tokens, PushMessage message) {
            sentTokens.add(tokens);
            lastMessage = message;
            return nextResult;
        }
    }

    @Autowired
    private NotificationPushService pushService;

    @Autowired
    private StubPushSender pushSender;

    @Autowired
    private TestEntityManager entityManager;

    @MockitoBean
    private PushTokenRepository pushTokenRepository;

    private User recipient;
    private ChatRoom chatRoom;

    @BeforeEach
    void setUp() {
        pushSender.reset();
        recipient = entityManager.persist(user("수신자"));
        Companion companion = entityManager.persist(companionPost(recipient));
        chatRoom = entityManager.persist(ChatRoom.create(companion));
        entityManager.flush();
    }

    @Nested
    @DisplayName("process (선점한 작업 발송·결과 기록)")
    class Process {

        @Test
        @DisplayName("성공하면 SENT로 바꾸고 발송 시각을 남긴다")
        void sent() {
            NotificationPushJob job = sendingJob(1);
            tokens("phone");
            pushSender.willReturn(TokenSendResult.success("phone"));

            pushService.process(job.getId());

            NotificationPushJob found = reload(job);
            assertThat(found.getStatus()).isEqualTo(PushJobStatus.SENT);
            assertThat(found.getSentAt()).isEqualTo(NOW);
        }

        @Test
        @DisplayName("토큰 중 하나라도 성공하면 나머지가 실패해도 SENT다")
        void partialSuccess() {
            NotificationPushJob job = sendingJob(1);
            tokens("phone", "pc");
            pushSender.willReturn(
                    TokenSendResult.success("phone"),
                    TokenSendResult.failure("pc", TokenSendOutcome.TRANSIENT_FAILURE, "UNAVAILABLE"));

            pushService.process(job.getId());

            assertThat(reload(job).getStatus()).isEqualTo(PushJobStatus.SENT);
        }

        @Test
        @DisplayName("수신자 토큰이 없으면 보내지 않고 SKIPPED로 바꾼다")
        void skipped() {
            NotificationPushJob job = sendingJob(1);
            tokens();

            pushService.process(job.getId());

            assertThat(reload(job).getStatus()).isEqualTo(PushJobStatus.SKIPPED);
            assertThat(pushSender.sentTokens).isEmpty();
        }

        @Test
        @DisplayName("일시 오류이고 시도 횟수가 남았으면 PENDING으로 되돌리고 30초 뒤 재시도를 예약한다")
        void retry() {
            NotificationPushJob job = sendingJob(1);
            tokens("phone");
            pushSender.willReturn(TokenSendResult.failure("phone", TokenSendOutcome.TRANSIENT_FAILURE, "UNAVAILABLE"));

            pushService.process(job.getId());

            NotificationPushJob found = reload(job);
            assertThat(found.getStatus()).isEqualTo(PushJobStatus.PENDING);
            assertThat(found.getNextAttemptAt()).isEqualTo(RETRY_AT);
            assertThat(found.getLastError()).isEqualTo("UNAVAILABLE");
        }

        @Test
        @DisplayName("일시 오류라도 3번째 시도였으면 FAILED로 바꾼다")
        void failedAfterMaxAttempts() {
            NotificationPushJob job = sendingJob(3);
            tokens("phone");
            pushSender.willReturn(TokenSendResult.failure("phone", TokenSendOutcome.TRANSIENT_FAILURE, "UNAVAILABLE"));

            pushService.process(job.getId());

            NotificationPushJob found = reload(job);
            assertThat(found.getStatus()).isEqualTo(PushJobStatus.FAILED);
            assertThat(found.getLastError()).isEqualTo("UNAVAILABLE");
        }

        @Test
        @DisplayName("재시도해도 실패하는 오류만 있으면 1번째 시도여도 FAILED로 바꾼다")
        void failedPermanently() {
            NotificationPushJob job = sendingJob(1);
            tokens("phone");
            pushSender.willReturn(
                    TokenSendResult.failure("phone", TokenSendOutcome.PERMANENT_FAILURE, "INVALID_ARGUMENT"));

            pushService.process(job.getId());

            assertThat(reload(job).getStatus()).isEqualTo(PushJobStatus.FAILED);
        }

        @Test
        @DisplayName("UNREGISTERED 토큰은 삭제를 요청한다")
        void deletesUnregisteredTokens() {
            NotificationPushJob job = sendingJob(1);
            tokens("phone", "old");
            pushSender.willReturn(
                    TokenSendResult.success("phone"),
                    TokenSendResult.failure("old", TokenSendOutcome.UNREGISTERED, "UNREGISTERED"));

            pushService.process(job.getId());

            verify(pushTokenRepository).deleteAllByTokenIn(List.of("old"));
        }

        @Test
        @DisplayName("UNREGISTERED 토큰이 없으면 삭제를 요청하지 않는다")
        void noDeleteWhenNoUnregistered() {
            NotificationPushJob job = sendingJob(1);
            tokens("phone");
            pushSender.willReturn(TokenSendResult.success("phone"));

            pushService.process(job.getId());

            verify(pushTokenRepository, never()).deleteAllByTokenIn(any());
        }

        @Test
        @DisplayName("푸시 제목은 모여타, 본문은 알림 content, data에는 이동에 필요한 값이 들어간다")
        void message() {
            NotificationPushJob job = sendingJob(1);
            tokens("phone");
            pushSender.willReturn(TokenSendResult.success("phone"));

            pushService.process(job.getId());

            PushMessage message = pushSender.lastMessage;
            assertThat(message.title()).isEqualTo(NotificationPushService.PUSH_TITLE);
            assertThat(message.body()).isEqualTo("민준님이 동승을 요청했어요");
            assertThat(message.data())
                    .containsEntry("notification_id", String.valueOf(job.getNotification().getId()))
                    .containsEntry("type", "CARPOOL_REQUEST_RECEIVED")
                    .containsEntry("target_chat_room_id", String.valueOf(chatRoom.getId()))
                    .doesNotContainKey("target_post_id");
        }

        @Test
        @DisplayName("선점(SENDING) 상태가 아닌 작업은 보내지 않는다")
        void ignoresNotSending() {
            NotificationPushJob job = jobWith(PushJobStatus.SENT, 1, NOW);
            tokens("phone");

            pushService.process(job.getId());

            assertThat(pushSender.sentTokens).isEmpty();
            assertThat(reload(job).getStatus()).isEqualTo(PushJobStatus.SENT);
        }
    }

    @Nested
    @DisplayName("sendImmediately (즉시 발송)")
    class SendImmediately {

        @Test
        @DisplayName("PENDING 작업을 선점해서 보내고 SENT로 바꾼다")
        void claimAndSend() {
            NotificationPushJob job = jobWith(PushJobStatus.PENDING, 0, NOW);
            tokens("phone");
            pushSender.willReturn(TokenSendResult.success("phone"));

            pushService.sendImmediately(job.getId());

            NotificationPushJob found = reload(job);
            assertThat(found.getStatus()).isEqualTo(PushJobStatus.SENT);
            assertThat(found.getAttempts()).isEqualTo(1);
        }

        @Test
        @DisplayName("이미 다른 쪽(스윕)이 선점한 작업이면 보내지 않는다")
        void alreadyClaimed() {
            NotificationPushJob job = jobWith(PushJobStatus.SENDING, 1, LEASE_UNTIL);
            tokens("phone");

            pushService.sendImmediately(job.getId());

            assertThat(pushSender.sentTokens).isEmpty();
            assertThat(reload(job).getAttempts()).isEqualTo(1);
        }
    }

    @Nested
    @DisplayName("claimDueJobs (스윕 배치 선점)")
    class ClaimDueJobs {

        @Test
        @DisplayName("재시도 시각이 된 PENDING과 선점 만료가 지난 SENDING을 선점하고 id를 돌려준다")
        void claimDue() {
            NotificationPushJob duePending = jobWith(PushJobStatus.PENDING, 1, NOW.minusSeconds(1));
            NotificationPushJob expiredSending = jobWith(PushJobStatus.SENDING, 1, NOW.minusSeconds(1));
            NotificationPushJob notYet = jobWith(PushJobStatus.PENDING, 1, NOW.plusSeconds(10));

            List<Long> claimed = pushService.claimDueJobs();

            assertThat(claimed).containsExactly(duePending.getId(), expiredSending.getId());
            NotificationPushJob foundPending = reload(duePending);
            assertThat(foundPending.getStatus()).isEqualTo(PushJobStatus.SENDING);
            assertThat(foundPending.getAttempts()).isEqualTo(2);
            assertThat(foundPending.getNextAttemptAt()).isEqualTo(LEASE_UNTIL);
            assertThat(reload(notYet).getStatus()).isEqualTo(PushJobStatus.PENDING);
        }

        @Test
        @DisplayName("대상이 없으면 빈 목록이다")
        void nothing() {
            jobWith(PushJobStatus.SENT, 1, NOW.minusDays(1));

            assertThat(pushService.claimDueJobs()).isEmpty();
        }
    }

    private void tokens(String... tokens) {
        given(pushTokenRepository.findTokensByUserId(recipient.getId())).willReturn(List.of(tokens));
    }

    private NotificationPushJob sendingJob(int attempts) {
        return jobWith(PushJobStatus.SENDING, attempts, LEASE_UNTIL);
    }

    private long nextEventId = 1L;

    private NotificationPushJob jobWith(PushJobStatus status, int attempts, LocalDateTime nextAttemptAt) {
        Notification notification = entityManager.persist(Notification.createForChatRoom(
                recipient, NotificationType.CARPOOL_REQUEST_RECEIVED, nextEventId++, chatRoom, "민준님이 동승을 요청했어요"));
        NotificationPushJob job = NotificationPushJob.create(notification, NOW.minusMinutes(1));
        ReflectionTestUtils.setField(job, "status", status);
        ReflectionTestUtils.setField(job, "attempts", attempts);
        ReflectionTestUtils.setField(job, "nextAttemptAt", nextAttemptAt);
        NotificationPushJob saved = entityManager.persistAndFlush(job);
        entityManager.clear();
        return saved;
    }

    private NotificationPushJob reload(NotificationPushJob job) {
        entityManager.flush();
        entityManager.clear();
        return entityManager.find(NotificationPushJob.class, job.getId());
    }
}
