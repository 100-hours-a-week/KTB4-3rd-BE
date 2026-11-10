package com.ktb.moyeota.domain.notification.service;

import com.ktb.moyeota.domain.notification.entity.NotificationPushJob;
import com.ktb.moyeota.domain.notification.entity.PushJobStatus;
import com.ktb.moyeota.domain.notification.message.NotificationPushChannel;
import com.ktb.moyeota.domain.notification.repository.NotificationPushJobRepository;
import com.ktb.moyeota.domain.notification.repository.PushJobTypeProjection;
import com.ktb.moyeota.global.config.NotificationPushKafkaProperties;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@Slf4j
@Service
@ConditionalOnProperty(prefix = "moyeota.notification.push", name = "mode", havingValue = "kafka")
public class NotificationPushRepublisher {

    static final String ATTEMPTS_EXHAUSTED = "SWEEP_ATTEMPTS_EXHAUSTED";

    private static final List<PushJobStatus> REPUBLISHABLE = List.of(PushJobStatus.PENDING, PushJobStatus.SENDING);

    private final NotificationPushJobRepository jobRepository;
    private final NotificationPushPublisher publisher;
    private final NotificationPushKafkaProperties properties;
    private final TransactionTemplate transactionTemplate;
    private final Clock clock;

    public NotificationPushRepublisher(NotificationPushJobRepository jobRepository,
                                       NotificationPushPublisher publisher,
                                       NotificationPushKafkaProperties properties,
                                       PlatformTransactionManager transactionManager,
                                       Clock clock) {
        this.jobRepository = jobRepository;
        this.publisher = publisher;
        this.properties = properties;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
        this.clock = clock;
    }

    public int republishDue() {
        LocalDateTime now = LocalDateTime.now(clock).truncatedTo(ChronoUnit.MICROS);
        Map<NotificationPushChannel, List<Long>> due = transactionTemplate.execute(status -> requeueDue(now));
        if (due == null || due.isEmpty()) {
            return 0;
        }
        due.forEach(publisher::publish);
        return due.values().stream().mapToInt(List::size).sum();
    }

    private Map<NotificationPushChannel, List<Long>> requeueDue(LocalDateTime now) {
        List<NotificationPushJob> due = new ArrayList<>(jobRepository.findClaimableForUpdate(
                REPUBLISHABLE,
                now.minus(properties.republishAfter()),
                PageRequest.of(0, properties.republishBatchSize())));
        int room = properties.republishBatchSize() - due.size();
        if (room > 0) {
            List<NotificationPushJob> stuck = jobRepository.findClaimableForUpdate(
                    List.of(PushJobStatus.PUBLISHED),
                    now.minus(properties.republishPublishedAfter()),
                    PageRequest.of(0, room));
            if (!stuck.isEmpty()) {
                log.warn("[PUSH_PUBLISHED_STUCK] jobs={}", idsOf(stuck));
            }
            due.addAll(stuck);
        }
        Map<Boolean, List<Long>> byAttemptsLeft = due.stream().collect(Collectors.partitioningBy(
                job -> job.hasAttemptsLeft(properties.republishMaxAttempts()),
                Collectors.mapping(NotificationPushJob::getId, Collectors.toList())));
        List<Long> exhausted = byAttemptsLeft.get(false);
        if (!exhausted.isEmpty()) {
            int failed = jobRepository.failExhausted(
                    exhausted, properties.republishMaxAttempts(), now, ATTEMPTS_EXHAUSTED);
            log.warn("[PUSH_SWEEP_GAVE_UP] failed={}, candidates={}, maxAttempts={}",
                    failed, exhausted, properties.republishMaxAttempts());
        }
        List<Long> ids = byAttemptsLeft.get(true);
        if (ids.isEmpty()) {
            return Map.of();
        }
        jobRepository.requeueAll(ids, now);
        return jobRepository.findTypesByIdIn(ids).stream()
                .collect(Collectors.groupingBy(
                        row -> NotificationPushChannel.of(row.getType()),
                        () -> new EnumMap<>(NotificationPushChannel.class),
                        Collectors.mapping(PushJobTypeProjection::getId, Collectors.toList())));
    }

    private static List<Long> idsOf(List<NotificationPushJob> jobs) {
        return jobs.stream().map(NotificationPushJob::getId).toList();
    }
}
