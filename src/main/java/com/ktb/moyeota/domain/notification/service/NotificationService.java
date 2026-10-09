package com.ktb.moyeota.domain.notification.service;

import com.ktb.moyeota.domain.notification.dto.NotificationListQuery;
import com.ktb.moyeota.domain.notification.dto.NotificationListResponse;
import com.ktb.moyeota.domain.notification.dto.NotificationReadResponse;
import com.ktb.moyeota.domain.notification.dto.NotificationResponse;
import com.ktb.moyeota.domain.notification.entity.Notification;
import com.ktb.moyeota.domain.notification.exception.NotificationErrorCode;
import com.ktb.moyeota.domain.notification.repository.NotificationRepository;
import com.ktb.moyeota.global.exception.BusinessException;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class NotificationService {

    private static final int PAGE_SIZE = 20;

    private final NotificationRepository notificationRepository;
    private final Clock clock;

    @Transactional(readOnly = true)
    public NotificationListResponse findList(Long userId, NotificationListQuery query) {

        if (query.isReadTab()) {
            throw new BusinessException(NotificationErrorCode.UNSUPPORTED_TAB);
        }
        if (!query.isValidTab()) {
            throw new BusinessException(NotificationErrorCode.INVALID_TAB);
        }
        if (!query.isValidCursor()) {
            throw new BusinessException(NotificationErrorCode.INVALID_CURSOR);
        }

        List<Notification> fetched = notificationRepository.findByRecipientWithCursor(
                userId, query.unreadOnly(), query.cursorId(), PageRequest.of(0, PAGE_SIZE + 1));

        boolean hasNext = fetched.size() > PAGE_SIZE;
        List<Notification> page = hasNext ? fetched.subList(0, PAGE_SIZE) : fetched;
        String nextCursor = hasNext ? String.valueOf(page.get(page.size() - 1).getId()) : null;

        List<NotificationResponse> notifications = page.stream()
                .map(NotificationResponse::from)
                .toList();

        return new NotificationListResponse(notifications, nextCursor);
    }

    @Transactional
    public NotificationReadResponse markRead(Long userId, Long notificationId) {

        LocalDateTime now = LocalDateTime.now(clock).truncatedTo(ChronoUnit.MICROS);

        int updated = notificationRepository.markReadIfUnread(notificationId, userId, now);
        if (updated == 1) {
            return new NotificationReadResponse(now);
        }

        LocalDateTime readAt = notificationRepository.findReadAtByIdAndRecipientId(notificationId, userId)
                .orElseThrow(() -> new BusinessException(NotificationErrorCode.NOTIFICATION_NOT_FOUND));
        return new NotificationReadResponse(readAt);
    }
}
