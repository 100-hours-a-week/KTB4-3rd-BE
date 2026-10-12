package com.ktb.moyeota.domain.notification.service;

import com.ktb.moyeota.domain.notification.entity.Notification;
import com.ktb.moyeota.domain.notification.entity.NotificationPushJob;
import com.ktb.moyeota.domain.notification.entity.PushJobStatus;
import com.ktb.moyeota.domain.notification.push.PushMessage;
import com.ktb.moyeota.domain.notification.push.PushSendResult;
import com.ktb.moyeota.domain.notification.push.PushSender;
import com.ktb.moyeota.domain.notification.repository.NotificationPushJobRepository;
import com.ktb.moyeota.domain.user.repository.PushTokenRepository;
import com.ktb.moyeota.global.config.NotificationPushProperties;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;


@Slf4j
@Service
public class NotificationPushService {

    static final String PUSH_TITLE = "모여타";

    private static final List<PushJobStatus> CLAIMABLE = List.of(PushJobStatus.PENDING, PushJobStatus.SENDING);

    private final NotificationPushJobRepository jobRepository;
    private final PushTokenRepository pushTokenRepository;
    private final PushSender pushSender;
    private final NotificationPushProperties properties;
    private final Clock clock;
    private final TransactionTemplate transactionTemplate;

    public NotificationPushService(NotificationPushJobRepository jobRepository,
                                   PushTokenRepository pushTokenRepository,
                                   PushSender pushSender,
                                   NotificationPushProperties properties,
                                   Clock clock,
                                   PlatformTransactionManager transactionManager) {
        this.jobRepository = jobRepository;
        this.pushTokenRepository = pushTokenRepository;
        this.pushSender = pushSender;
        this.properties = properties;
        this.clock = clock;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    public void sendImmediately(Long jobId) {
        if (claim(jobId)) {
            process(jobId);
        }
    }

    public boolean claim(Long jobId) {
        LocalDateTime now = now();
        Integer claimed = transactionTemplate.execute(
                status -> jobRepository.claimPending(jobId, now, now.plus(properties.lease())));
        return claimed != null && claimed == 1;
    }

    public List<Long> claimDueJobs() {
        LocalDateTime now = now();
        List<Long> ids = transactionTemplate.execute(status -> {
            List<Long> due = jobRepository.findClaimableForUpdate(
                            CLAIMABLE, now, PageRequest.of(0, properties.batchSize()))
                    .stream()
                    .map(NotificationPushJob::getId)
                    .toList();
            if (!due.isEmpty()) {
                jobRepository.claimAll(due, now, now.plus(properties.lease()));
            }
            return due;
        });
        return ids == null ? List.of() : ids;
    }

    public void process(Long jobId) {
        PushTarget target = transactionTemplate.execute(status -> loadTarget(jobId));
        if (target == null) {
            return;
        }

        PushSendResult result = target.tokens().isEmpty()
                ? null
                : pushSender.send(target.tokens(), target.message());

        transactionTemplate.executeWithoutResult(status -> record(jobId, result));
    }

    private PushTarget loadTarget(Long jobId) {
        return jobRepository.findWithNotificationById(jobId)
                .filter(job -> job.getStatus() == PushJobStatus.SENDING)
                .map(job -> {
                    Notification notification = job.getNotification();
                    List<String> tokens = pushTokenRepository.findTokensByUserId(notification.getRecipient().getId());
                    return new PushTarget(tokens, toMessage(notification));
                })
                .orElse(null);
    }

    private void record(Long jobId, PushSendResult result) {
        if (result != null && !result.unregisteredTokens().isEmpty()) {
            pushTokenRepository.deleteAllByTokenIn(result.unregisteredTokens());
        }

        NotificationPushJob job = jobRepository.findById(jobId).orElse(null);
        if (job == null || job.getStatus() != PushJobStatus.SENDING) {
            return;
        }

        LocalDateTime now = now();
        if (result == null) {
            job.markSkipped(now);
            return;
        }
        applyResult(job, result, now);
    }


    void applyResult(NotificationPushJob job, PushSendResult result, LocalDateTime now) {
        if (result.anySuccess()) {
            job.markSent(now);
            return;
        }

        String error = result.errorSummary();
        if (result.hasTransientFailure() && job.hasAttemptsLeft(properties.maxAttempts())) {
            job.scheduleRetry(now, now.plus(properties.retryDelay()), error);
            return;
        }

        job.markFailed(now, error);
        log.warn("[PUSH_FAILED] jobId={}, notificationId={}, attempts={}, lastError={}",
                job.getId(), job.getNotification().getId(), job.getAttempts(), error);
    }

    static PushMessage toMessage(Notification notification) {
        Map<String, String> data = new LinkedHashMap<>();
        data.put("notification_id", String.valueOf(notification.getId()));
        data.put("type", notification.getType().name());
        if (notification.getTargetChatRoom() != null) {
            data.put("target_chat_room_id", String.valueOf(notification.getTargetChatRoom().getId()));
        }
        if (notification.getTargetPost() != null) {
            data.put("target_post_id", String.valueOf(notification.getTargetPost().getId()));
        }
        return new PushMessage(PUSH_TITLE, notification.getContent(), data);
    }

    private LocalDateTime now() {
        return LocalDateTime.now(clock).truncatedTo(ChronoUnit.MICROS);
    }

    private record PushTarget(List<String> tokens, PushMessage message) {
    }
}
