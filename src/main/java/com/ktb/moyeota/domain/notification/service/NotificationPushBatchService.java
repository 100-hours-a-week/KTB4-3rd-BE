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
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class NotificationPushBatchService {

    private static final Set<PushJobStatus> REQUESTED = EnumSet.of(PushJobStatus.PENDING, PushJobStatus.PUBLISHED);

    private final NotificationPushJobRepository jobRepository;
    private final PushTokenRepository pushTokenRepository;
    private final PushSender pushSender;
    private final NotificationPushService pushService;
    private final NotificationPushProperties properties;
    private final Clock clock;
    private final TransactionTemplate transactionTemplate;

    public NotificationPushBatchService(NotificationPushJobRepository jobRepository,
                                        PushTokenRepository pushTokenRepository,
                                        PushSender pushSender,
                                        NotificationPushService pushService,
                                        NotificationPushProperties properties,
                                        Clock clock,
                                        PlatformTransactionManager transactionManager) {
        this.jobRepository = jobRepository;
        this.pushTokenRepository = pushTokenRepository;
        this.pushSender = pushSender;
        this.pushService = pushService;
        this.properties = properties;
        this.clock = clock;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    public Map<Long, PushDeliveryOutcome> deliverAll(List<Long> jobIds, int maxAttempts, Executor sendExecutor) {
        Map<Long, PushDeliveryOutcome> outcomes = new HashMap<>();
        jobIds.forEach(id -> outcomes.put(id, PushDeliveryOutcome.NOT_CLAIMED));
        if (jobIds.isEmpty()) {
            return outcomes;
        }
        List<Long> claimed = claimAll(jobIds);
        if (claimed.isEmpty()) {
            return outcomes;
        }
        Map<Long, Target> targets = loadTargets(claimed);
        Map<Long, PushSendResult> results = send(targets, sendExecutor);
        outcomes.putAll(recordAll(claimed, results, maxAttempts));
        return outcomes;
    }

    private List<Long> claimAll(List<Long> jobIds) {
        LocalDateTime now = now();
        List<Long> claimed = transactionTemplate.execute(status -> {
            List<Long> ids = jobRepository.findAllByIdInForUpdate(jobIds).stream()
                    .filter(job -> REQUESTED.contains(job.getStatus()))
                    .map(NotificationPushJob::getId)
                    .toList();
            if (!ids.isEmpty()) {
                jobRepository.claimAll(ids, now, now.plus(properties.lease()));
            }
            return ids;
        });
        return claimed == null ? List.of() : claimed;
    }

    private Map<Long, Target> loadTargets(List<Long> claimed) {
        Map<Long, Target> targets = transactionTemplate.execute(status -> {
            List<NotificationPushJob> jobs = jobRepository.findAllWithNotificationByIdIn(claimed).stream()
                    .filter(job -> job.getStatus() == PushJobStatus.SENDING)
                    .toList();
            List<Long> userIds = jobs.stream().map(job -> job.getNotification().getRecipient().getId()).distinct().toList();
            Map<Long, List<String>> tokensByUser = new HashMap<>();
            if (!userIds.isEmpty()) {
                for (Object[] row : pushTokenRepository.findUserIdAndTokenByUserIdIn(userIds)) {
                    tokensByUser.computeIfAbsent((Long) row[0], ignored -> new ArrayList<>()).add((String) row[1]);
                }
            }
            Map<Long, Target> loaded = new LinkedHashMap<>();
            for (NotificationPushJob job : jobs) {
                Notification notification = job.getNotification();
                List<String> tokens = tokensByUser.getOrDefault(notification.getRecipient().getId(), List.of());
                loaded.put(job.getId(), new Target(tokens, NotificationPushService.toMessage(notification)));
            }
            return loaded;
        });
        return targets == null ? Map.of() : targets;
    }

    private Map<Long, PushSendResult> send(Map<Long, Target> targets, Executor sendExecutor) {
        Map<Long, PushSendResult> results = new HashMap<>();
        if (sendExecutor == null) {
            targets.forEach((jobId, target) -> results.put(jobId, sendOne(target)));
            return results;
        }
        Map<Long, CompletableFuture<PushSendResult>> futures = new LinkedHashMap<>();
        targets.forEach((jobId, target) -> futures.put(jobId,
                CompletableFuture.supplyAsync(() -> sendOne(target), sendExecutor)));
        futures.forEach((jobId, future) -> results.put(jobId, future.join()));
        return results;
    }

    private PushSendResult sendOne(Target target) {
        return target.tokens().isEmpty() ? null : pushSender.send(target.tokens(), target.message());
    }

    private Map<Long, PushDeliveryOutcome> recordAll(List<Long> claimed, Map<Long, PushSendResult> results,
                                                     int maxAttempts) {
        Map<Long, PushDeliveryOutcome> recorded = transactionTemplate.execute(status -> {
            List<String> unregistered = results.values().stream()
                    .filter(result -> result != null)
                    .flatMap(result -> result.unregisteredTokens().stream())
                    .toList();
            if (!unregistered.isEmpty()) {
                pushTokenRepository.deleteAllByTokenIn(unregistered);
            }
            LocalDateTime now = now();
            Map<Long, PushDeliveryOutcome> outcomes = new HashMap<>();
            for (NotificationPushJob job : jobRepository.findAllById(claimed)) {
                if (job.getStatus() != PushJobStatus.SENDING) {
                    outcomes.put(job.getId(), PushDeliveryOutcome.NOT_CLAIMED);
                    continue;
                }
                PushSendResult result = results.get(job.getId());
                if (result == null) {
                    job.markSkipped(now);
                    outcomes.put(job.getId(), PushDeliveryOutcome.SKIPPED);
                    continue;
                }
                outcomes.put(job.getId(), pushService.applyResult(job, result, now, maxAttempts));
            }
            return outcomes;
        });
        return recorded == null ? Map.of() : recorded;
    }

    private LocalDateTime now() {
        return LocalDateTime.now(clock).truncatedTo(ChronoUnit.MICROS);
    }

    private record Target(List<String> tokens, PushMessage message) {
    }
}
