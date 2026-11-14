package com.ktb.moyeota.domain.notification.service;

import com.ktb.moyeota.domain.chat.entity.ChatRoom;
import com.ktb.moyeota.domain.chat.entity.CompanionParticipant;
import com.ktb.moyeota.domain.companion.entity.Companion;
import com.ktb.moyeota.domain.notification.entity.SettlementReminderText;
import com.ktb.moyeota.domain.notification.event.NotificationBulkPushRequestedEvent;
import com.ktb.moyeota.domain.notification.repository.ReminderState;
import com.ktb.moyeota.domain.notification.repository.SettlementReminderRepository;
import com.ktb.moyeota.domain.notification.repository.SettlementReminderWriter;
import com.ktb.moyeota.domain.notification.repository.SettlementReminderWriter.NewReminder;
import com.ktb.moyeota.domain.notification.repository.SettlementReminderWriter.OpenedJob;
import com.ktb.moyeota.domain.notification.repository.SettlementReminderWriter.RemindedNotification;
import com.ktb.moyeota.domain.settlement.entity.Settlement;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class SettlementReminderService {

    private final SettlementReminderRepository reminderRepository;
    private final SettlementReminderWriter reminderWriter;
    private final ApplicationEventPublisher eventPublisher;
    private final Clock clock;

    @Transactional(readOnly = true)
    public List<Long> findSettlementIdsToRemind() {
        return reminderRepository.findSettlementIdsToRemind();
    }

    @Transactional
    public void remind(Long settlementId, LocalDateTime round) {
        remindAll(List.of(settlementId), round);
    }

    @Transactional
    public void remindAll(List<Long> settlementIds, LocalDateTime round) {
        Writes writes = new Writes(now());
        for (Long settlementId : settlementIds) {
            reminderRepository.findRequestedForUpdate(settlementId)
                    .ifPresent(settlement -> collect(settlement, round, writes));
        }
        writes.apply(reminderWriter).forEach((settlementId, jobIds) ->
                eventPublisher.publishEvent(new NotificationBulkPushRequestedEvent(jobIds)));
    }

    private void collect(Settlement settlement, LocalDateTime round, Writes writes) {
        Companion taxiPot = settlement.getCompanion();
        ChatRoom chatRoom = reminderRepository.findChatRoom(taxiPot.getId())
                .orElseThrow(() -> new IllegalStateException("택시팟에 채팅방이 없다: " + taxiPot.getId()));
        Long hostId = chatRoom.getCompanion().getHost().getId();
        List<CompanionParticipant> participants = reminderRepository.findPendingParticipants(taxiPot.getId());
        if (participants.isEmpty()) {
            return;
        }
        List<Long> recipientIds = participants.stream().map(participant -> participant.getUser().getId()).toList();
        Map<Long, ReminderState> states = reminderRepository.findReminders(recipientIds, settlement.getId()).stream()
                .collect(Collectors.toMap(ReminderState::recipientId, Function.identity()));
        for (Long recipientId : recipientIds) {
            String content = SettlementReminderText.of(recipientId.equals(hostId)).getText();
            writes.add(recipientId, settlement.getId(), chatRoom.getId(), content, states.get(recipientId), round);
        }
    }

    private LocalDateTime now() {
        return LocalDateTime.now(clock).truncatedTo(ChronoUnit.MICROS);
    }

    private static final class Writes {

        private final LocalDateTime now;
        private final List<NewReminder> created = new ArrayList<>();
        private final List<RemindedNotification> reminded = new ArrayList<>();
        private final List<Long> reopenedJobIds = new ArrayList<>();
        private final Set<Long> recipientIds = new LinkedHashSet<>();
        private final Set<Long> settlementIds = new LinkedHashSet<>();

        private Writes(LocalDateTime now) {
            this.now = now;
        }

        private void add(Long recipientId, Long settlementId, Long chatRoomId, String content, ReminderState state,
                         LocalDateTime round) {
            if (state == null) {
                created.add(new NewReminder(recipientId, settlementId, chatRoomId, content));
            } else if (state.remindedSince(round)) {
                return;
            } else {
                reminded.add(new RemindedNotification(state.notificationId(), content));
                if (state.hasJob() && state.jobFinished()) {
                    reopenedJobIds.add(state.jobId());
                }
            }
            recipientIds.add(recipientId);
            settlementIds.add(settlementId);
        }

        private Map<Long, List<Long>> apply(SettlementReminderWriter writer) {
            if (recipientIds.isEmpty()) {
                return Map.of();
            }
            writer.remindNotifications(reminded, now);
            writer.insertNotifications(created, now);
            writer.reopenJobs(reopenedJobIds, now);
            writer.insertMissingJobs(recipientIds, settlementIds, now);
            Map<Long, List<Long>> jobIdsBySettlement = new LinkedHashMap<>();
            for (OpenedJob job : writer.findOpenedJobs(recipientIds, settlementIds, now)) {
                jobIdsBySettlement.computeIfAbsent(job.settlementId(), id -> new ArrayList<>()).add(job.jobId());
            }
            return jobIdsBySettlement;
        }
    }
}
