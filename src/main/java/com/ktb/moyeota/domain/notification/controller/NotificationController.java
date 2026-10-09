package com.ktb.moyeota.domain.notification.controller;

import com.ktb.moyeota.domain.notification.dto.NotificationListQuery;
import com.ktb.moyeota.domain.notification.dto.NotificationListResponse;
import com.ktb.moyeota.domain.notification.dto.NotificationReadResponse;
import com.ktb.moyeota.domain.notification.service.NotificationService;
import com.ktb.moyeota.domain.notification.success.NotificationSuccessCode;
import com.ktb.moyeota.global.common.ApiResponse;
import com.ktb.moyeota.global.security.resolver.AuthUser;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/notifications")
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationService notificationService;

    @GetMapping
    public ResponseEntity<ApiResponse<NotificationListResponse>> list(
            @AuthUser Long userId,
            NotificationListQuery query
    ) {
        NotificationListResponse response = notificationService.findList(userId, query);
        NotificationSuccessCode code = response.notifications().isEmpty()
                ? NotificationSuccessCode.NO_NOTIFICATIONS
                : NotificationSuccessCode.NOTIFICATIONS_FOUND;
        return ResponseEntity.ok(ApiResponse.of(code, response));
    }

    @PatchMapping("/{notification_id}/read_at")
    public ResponseEntity<ApiResponse<NotificationReadResponse>> markRead(
            @AuthUser Long userId,
            @PathVariable("notification_id") Long notificationId
    ) {
        NotificationReadResponse response = notificationService.markRead(userId, notificationId);
        return ResponseEntity.ok(ApiResponse.of(NotificationSuccessCode.NOTIFICATION_READ, response));
    }
}
