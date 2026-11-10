package com.ktb.moyeota.domain.notification.repository;

import com.ktb.moyeota.domain.notification.entity.NotificationType;

public interface PushJobTypeProjection {

    Long getId();

    NotificationType getType();
}
