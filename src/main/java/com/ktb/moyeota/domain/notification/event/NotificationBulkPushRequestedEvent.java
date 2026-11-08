package com.ktb.moyeota.domain.notification.event;

import java.util.List;

public record NotificationBulkPushRequestedEvent(
        List<Long> jobIds
) {

    public NotificationBulkPushRequestedEvent {
        jobIds = List.copyOf(jobIds);
    }
}
