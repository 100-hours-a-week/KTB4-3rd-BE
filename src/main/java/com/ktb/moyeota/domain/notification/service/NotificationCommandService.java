package com.ktb.moyeota.domain.notification.service;

import com.ktb.moyeota.domain.chat.entity.ChatRoom;
import com.ktb.moyeota.domain.community.entity.CommunityPost;
import com.ktb.moyeota.domain.notification.entity.Notification;
import com.ktb.moyeota.domain.notification.entity.NotificationPushJob;
import com.ktb.moyeota.domain.notification.entity.NotificationType;
import com.ktb.moyeota.domain.notification.event.NotificationPushRequestedEvent;
import com.ktb.moyeota.domain.notification.repository.NotificationPushJobRepository;
import com.ktb.moyeota.domain.notification.repository.NotificationRepository;
import com.ktb.moyeota.domain.user.entity.User;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;


@Service
@RequiredArgsConstructor
public class NotificationCommandService {

    private final NotificationRepository notificationRepository;
    private final NotificationPushJobRepository pushJobRepository;
    private final ApplicationEventPublisher eventPublisher;
    private final Clock clock;

    @Transactional
    public Notification createForChatRoom(User recipient, NotificationType type, Long eventId,
                                          ChatRoom targetChatRoom, String content) {
        return saveWithPushJob(Notification.createForChatRoom(recipient, type, eventId, targetChatRoom, content));
    }

    @Transactional
    public Notification createForComment(User recipient, Long commentId, CommunityPost targetPost, String content) {
        return saveWithPushJob(Notification.createForComment(recipient, commentId, targetPost, content));
    }

    private Notification saveWithPushJob(Notification notification) {
        Notification saved = notificationRepository.save(notification);
        NotificationPushJob job = pushJobRepository.save(NotificationPushJob.create(saved, now()));
        eventPublisher.publishEvent(new NotificationPushRequestedEvent(job.getId()));
        return saved;
    }

    private LocalDateTime now() {
        return LocalDateTime.now(clock).truncatedTo(ChronoUnit.MICROS);
    }
}
