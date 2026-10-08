package com.ktb.moyeota.domain.notification.repository;

import com.ktb.moyeota.domain.notification.entity.Notification;
import org.springframework.data.jpa.repository.JpaRepository;

public interface NotificationRepository extends JpaRepository<Notification, Long> {
}
