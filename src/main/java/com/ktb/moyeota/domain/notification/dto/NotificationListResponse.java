package com.ktb.moyeota.domain.notification.dto;

import java.util.List;

public record NotificationListResponse(
        List<NotificationResponse> notifications,
        String nextCursor
) {
}
