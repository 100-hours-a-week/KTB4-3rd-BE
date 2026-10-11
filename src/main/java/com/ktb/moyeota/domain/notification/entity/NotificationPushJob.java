package com.ktb.moyeota.domain.notification.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

@Entity
@Getter
@Table(
        name = "notification_push_jobs",
        uniqueConstraints = @UniqueConstraint(name = "uk_push_jobs_notification", columnNames = "notification_id"),
        indexes = @Index(name = "idx_push_jobs_status_next", columnList = "status, next_attempt_at"))
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class NotificationPushJob {

    private static final int LAST_ERROR_MAX_LENGTH = 255;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "notification_id", nullable = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    private Notification notification;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private PushJobStatus status;

    @Column(name = "attempts", nullable = false)
    private int attempts;

    @Column(name = "next_attempt_at", nullable = false)
    private LocalDateTime nextAttemptAt;

    @Column(name = "last_error", length = LAST_ERROR_MAX_LENGTH)
    private String lastError;

    @Column(name = "sent_at")
    private LocalDateTime sentAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    private NotificationPushJob(Notification notification, LocalDateTime now) {
        this.notification = notification;
        this.status = PushJobStatus.PENDING;
        this.attempts = 0;
        this.nextAttemptAt = now;
        this.createdAt = now;
        this.updatedAt = now;
    }

    /** 알림 저장과 같은 트랜잭션에서 생성한다. 바로 발송 대상(PENDING)이 된다. */
    public static NotificationPushJob create(Notification notification, LocalDateTime now) {
        if (notification == null) {
            throw new IllegalArgumentException("발송 작업에는 notification이 필요합니다");
        }
        return new NotificationPushJob(notification, now);
    }

    public void markSent(LocalDateTime now) {
        requireSending();
        this.status = PushJobStatus.SENT;
        this.sentAt = now;
        this.updatedAt = now;
    }

    public void scheduleRetry(LocalDateTime now, LocalDateTime retryAt, String error) {
        requireSending();
        this.status = PushJobStatus.PENDING;
        this.nextAttemptAt = retryAt;
        this.lastError = truncate(error);
        this.updatedAt = now;
    }

    public void markFailed(LocalDateTime now, String error) {
        requireSending();
        this.status = PushJobStatus.FAILED;
        this.lastError = truncate(error);
        this.updatedAt = now;
    }

    public void markSkipped(LocalDateTime now) {
        requireSending();
        this.status = PushJobStatus.SKIPPED;
        this.updatedAt = now;
    }

    public boolean hasAttemptsLeft(int maxAttempts) {
        return attempts < maxAttempts;
    }

    private void requireSending() {
        if (status != PushJobStatus.SENDING) {
            throw new IllegalStateException("선점(SENDING)된 작업만 결과를 기록할 수 있습니다. status=" + status);
        }
    }

    private static String truncate(String error) {
        if (error == null || error.length() <= LAST_ERROR_MAX_LENGTH) {
            return error;
        }
        return error.substring(0, LAST_ERROR_MAX_LENGTH);
    }
}
