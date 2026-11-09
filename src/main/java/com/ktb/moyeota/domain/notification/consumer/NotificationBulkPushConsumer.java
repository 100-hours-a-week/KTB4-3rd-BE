package com.ktb.moyeota.domain.notification.consumer;

import com.ktb.moyeota.domain.notification.message.NotificationPushMessages;
import com.ktb.moyeota.domain.notification.message.NotificationPushRequestData;
import com.ktb.moyeota.domain.notification.service.NotificationPushService;
import com.ktb.moyeota.domain.notification.service.PushDeliveryOutcome;
import com.ktb.moyeota.global.config.ConditionalOnServerRole;
import com.ktb.moyeota.global.config.ServerRole;
import com.ktb.moyeota.global.event.EventEnvelope;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnServerRole({ServerRole.WORKER, ServerRole.ALL})
@ConditionalOnProperty(prefix = "moyeota.notification.push", name = "mode", havingValue = "kafka")
public class NotificationBulkPushConsumer {

    public static final String GROUP_ID = "notification-bulk-push-sender";

    private final NotificationPushService pushService;

    @KafkaListener(
            topics = NotificationPushMessages.BULK_PUSH_TOPIC,
            groupId = GROUP_ID,
            containerFactory = NotificationBulkPushConsumerConfig.CONTAINER_FACTORY)
    public void consume(@Payload EventEnvelope<NotificationPushRequestData> envelope) {
        if (!NotificationPushMessages.BULK_PUSH_REQUESTED.equals(envelope.eventType())) {
            log.warn("[BULK_PUSH_REQUEST_UNKNOWN_EVENT] eventId={}, eventType={}",
                    envelope.eventId(), envelope.eventType());
            return;
        }
        envelope.requireSchemaVersion(NotificationPushMessages.SCHEMA_VERSION);
        long jobId = envelope.data().jobId();
        PushDeliveryOutcome outcome = pushService.deliver(jobId, NotificationBulkPushConsumerConfig.MAX_ATTEMPTS);
        if (outcome.retryable()) {
            throw new PushRetryException(jobId, outcome);
        }
        log.info("[BULK_PUSH_DELIVERED] eventId={}, jobId={}, outcome={}", envelope.eventId(), jobId, outcome);
    }
}
