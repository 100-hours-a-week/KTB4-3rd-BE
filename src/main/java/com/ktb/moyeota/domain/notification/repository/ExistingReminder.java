package com.ktb.moyeota.domain.notification.repository;

import com.ktb.moyeota.domain.notification.entity.Notification;
import com.ktb.moyeota.domain.notification.entity.NotificationPushJob;

public record ExistingReminder(Notification notification, NotificationPushJob job) {
}
