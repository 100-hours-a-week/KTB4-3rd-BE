package com.ktb.moyeota.domain.notification.repository;

import com.ktb.moyeota.domain.notification.entity.PushJobStatus;
import java.time.LocalDateTime;

public record ReminderState(Long notificationId, Long recipientId, LocalDateTime remindedAt, Long jobId,
                            PushJobStatus jobStatus) {

    public boolean remindedSince(LocalDateTime round) {
        return !remindedAt.isBefore(round);
    }

    public boolean hasJob() {
        return jobId != null;
    }

    public boolean jobFinished() {
        return jobStatus == PushJobStatus.SENT || jobStatus == PushJobStatus.FAILED
                || jobStatus == PushJobStatus.SKIPPED;
    }
}
