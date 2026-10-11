package com.ktb.moyeota.domain.notification.entity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

import java.time.LocalDateTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.test.util.ReflectionTestUtils;

class NotificationPushJobTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 12, 9, 0, 0);
    private static final LocalDateTime LATER = NOW.plusSeconds(30);

    @Nested
    @DisplayName("create")
    class Create {

        @Test
        @DisplayName("PENDING, 시도 0회, 바로 보낼 수 있는 상태로 생성된다")
        void pending() {
            NotificationPushJob job = NotificationPushJob.create(mock(Notification.class), NOW);

            assertThat(job.getStatus()).isEqualTo(PushJobStatus.PENDING);
            assertThat(job.getAttempts()).isZero();
            assertThat(job.getNextAttemptAt()).isEqualTo(NOW);
            assertThat(job.getCreatedAt()).isEqualTo(NOW);
            assertThat(job.getUpdatedAt()).isEqualTo(NOW);
            assertThat(job.getSentAt()).isNull();
            assertThat(job.getLastError()).isNull();
        }

        @Test
        @DisplayName("알림 없이는 만들 수 없다")
        void requiresNotification() {
            assertThatThrownBy(() -> NotificationPushJob.create(null, NOW))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Nested
    @DisplayName("결과 기록")
    class Result {

        @Test
        @DisplayName("markSent: SENT로 바꾸고 발송 시각을 남긴다")
        void markSent() {
            NotificationPushJob job = sending();

            job.markSent(LATER);

            assertThat(job.getStatus()).isEqualTo(PushJobStatus.SENT);
            assertThat(job.getSentAt()).isEqualTo(LATER);
            assertThat(job.getUpdatedAt()).isEqualTo(LATER);
        }

        @Test
        @DisplayName("scheduleRetry: PENDING으로 되돌리고 재시도 시각과 실패 사유를 남긴다")
        void scheduleRetry() {
            NotificationPushJob job = sending();
            LocalDateTime retryAt = LATER.plusSeconds(30);

            job.scheduleRetry(LATER, retryAt, "UNAVAILABLE");

            assertThat(job.getStatus()).isEqualTo(PushJobStatus.PENDING);
            assertThat(job.getNextAttemptAt()).isEqualTo(retryAt);
            assertThat(job.getLastError()).isEqualTo("UNAVAILABLE");
            assertThat(job.getSentAt()).isNull();
        }

        @Test
        @DisplayName("markFailed: FAILED로 바꾸고 실패 사유를 남긴다")
        void markFailed() {
            NotificationPushJob job = sending();

            job.markFailed(LATER, "INVALID_ARGUMENT");

            assertThat(job.getStatus()).isEqualTo(PushJobStatus.FAILED);
            assertThat(job.getLastError()).isEqualTo("INVALID_ARGUMENT");
        }

        @Test
        @DisplayName("markSkipped: SKIPPED로 바꾼다")
        void markSkipped() {
            NotificationPushJob job = sending();

            job.markSkipped(LATER);

            assertThat(job.getStatus()).isEqualTo(PushJobStatus.SKIPPED);
        }

        @Test
        @DisplayName("실패 사유가 255자를 넘으면 255자로 자른다")
        void truncateLastError() {
            NotificationPushJob job = sending();

            job.markFailed(LATER, "x".repeat(300));

            assertThat(job.getLastError()).hasSize(255);
        }

        @ParameterizedTest
        @EnumSource(value = PushJobStatus.class, names = "SENDING", mode = EnumSource.Mode.EXCLUDE)
        @DisplayName("선점(SENDING)되지 않은 작업에는 결과를 기록할 수 없다")
        void requiresSending(PushJobStatus status) {
            NotificationPushJob job = withStatus(status);

            assertThatThrownBy(() -> job.markSent(LATER)).isInstanceOf(IllegalStateException.class);
            assertThatThrownBy(() -> job.scheduleRetry(LATER, LATER, "e")).isInstanceOf(IllegalStateException.class);
            assertThatThrownBy(() -> job.markFailed(LATER, "e")).isInstanceOf(IllegalStateException.class);
            assertThatThrownBy(() -> job.markSkipped(LATER)).isInstanceOf(IllegalStateException.class);
        }
    }

    @Test
    @DisplayName("hasAttemptsLeft: 시도 횟수가 최대 횟수보다 적을 때만 true")
    void hasAttemptsLeft() {
        NotificationPushJob job = sending();

        ReflectionTestUtils.setField(job, "attempts", 2);
        assertThat(job.hasAttemptsLeft(3)).isTrue();

        ReflectionTestUtils.setField(job, "attempts", 3);
        assertThat(job.hasAttemptsLeft(3)).isFalse();
    }

    private static NotificationPushJob sending() {
        return withStatus(PushJobStatus.SENDING);
    }

    private static NotificationPushJob withStatus(PushJobStatus status) {
        NotificationPushJob job = NotificationPushJob.create(mock(Notification.class), NOW);
        ReflectionTestUtils.setField(job, "status", status);
        return job;
    }
}
