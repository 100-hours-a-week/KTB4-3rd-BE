package com.ktb.moyeota.domain.notification.dto;

import java.time.LocalDateTime;

public record NotificationReadResponse(
        LocalDateTime readAt
) {
}
