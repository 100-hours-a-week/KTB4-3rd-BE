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
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@Service
@ConditionalOnProperty(prefix = "moyeota.notification.push", name = "mode", havingValue = "kafka")
public class NotificationPushRepublisher {

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
        List<Long> ids = jobRepository.findClaimableForUpdate(
                        REPUBLISHABLE,
                        now.minus(properties.republishAfter()),
                        PageRequest.of(0, properties.republishBatchSize()))
                .stream()
                .map(NotificationPushJob::getId)
                .toList();
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
}
