package com.ktb.moyeota.domain.notification.service;

import com.ktb.moyeota.domain.chat.entity.ChatRoom;
import com.ktb.moyeota.domain.chat.entity.CompanionParticipant;
import com.ktb.moyeota.domain.companion.entity.Companion;
import com.ktb.moyeota.domain.notification.entity.Notification;
import com.ktb.moyeota.domain.notification.entity.NotificationPushJob;
import com.ktb.moyeota.domain.notification.entity.NotificationType;
import com.ktb.moyeota.domain.notification.repository.NotificationPushJobRepository;
import com.ktb.moyeota.domain.notification.repository.NotificationRepository;
import com.ktb.moyeota.domain.notification.repository.SettlementReminderRepository;
import com.ktb.moyeota.domain.settlement.entity.Settlement;
import com.ktb.moyeota.domain.user.entity.User;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class SettlementReminderService {

    static final String HOST_CONTENT =
            "운행 종료를 안했습니다. 운행을 종료해주세요. 운행 종료를 해야 다른 택시팟 매칭에 참여가 가능합니다.";
    static final String RIDER_CONTENT =
            "방장에게 운행 종료 요청을 해주세요. 운행 종료를 해야 다른 택시팟 매칭에 참여가 가능합니다.";

    private final SettlementReminderRepository reminderRepository;
    private final NotificationRepository notificationRepository;
    private final NotificationPushJobRepository pushJobRepository;
    private final Clock clock;

    @Transactional(readOnly = true)
    public List<Long> findSettlementIdsToRemind() {
        return reminderRepository.findSettlementIdsToRemind();
    }

    @Transactional
    public void remind(Long settlementId) {
        reminderRepository.findRequestedForUpdate(settlementId).ifPresent(this::remindParticipants);
    }

    private void remindParticipants(Settlement settlement) {
        Companion taxiPot = settlement.getCompanion();
        ChatRoom chatRoom = reminderRepository.findChatRoom(taxiPot.getId())
                .orElseThrow(() -> new IllegalStateException("택시팟에 채팅방이 없다: " + taxiPot.getId()));
        Long hostId = taxiPot.getHost().getId();
        LocalDateTime now = now();
        for (CompanionParticipant participant : reminderRepository.findPendingParticipants(taxiPot.getId())) {
            User recipient = participant.getUser();
            String content = recipient.getId().equals(hostId) ? HOST_CONTENT : RIDER_CONTENT;
            remindRecipient(recipient, settlement.getId(), chatRoom, content, now);
        }
    }

    private void remindRecipient(User recipient, Long settlementId, ChatRoom chatRoom, String content,
                                 LocalDateTime now) {
        notificationRepository.findByRecipientIdAndTypeAndEventId(
                        recipient.getId(), NotificationType.SETTLEMENT_COMPLETION_REMINDER, settlementId)
                .ifPresentOrElse(
                        notification -> resend(notification, content, now),
                        () -> create(recipient, settlementId, chatRoom, content, now));
    }

    private void create(User recipient, Long settlementId, ChatRoom chatRoom, String content, LocalDateTime now) {
        Notification notification = notificationRepository.save(
                Notification.createSettlementReminder(recipient, settlementId, chatRoom, content, now));
        pushJobRepository.save(NotificationPushJob.create(notification, now));
    }

    private void resend(Notification notification, String content, LocalDateTime now) {
        notification.remind(content, now);
        pushJobRepository.findByNotificationId(notification.getId())
                .ifPresentOrElse(
                        job -> job.reopen(now),
                        () -> pushJobRepository.save(NotificationPushJob.create(notification, now)));
    }

    private LocalDateTime now() {
        return LocalDateTime.now(clock).truncatedTo(ChronoUnit.MICROS);
    }
}
