package com.ktb.moyeota.domain.congestion.consumer;

import com.ktb.moyeota.domain.congestion.message.PostChangedData;
import com.ktb.moyeota.domain.congestion.message.PostChangedMessages;
import com.ktb.moyeota.domain.congestion.service.CongestionSignalService;
import com.ktb.moyeota.domain.congestion.service.SignalLoadOutcome;
import com.ktb.moyeota.global.config.ConditionalOnServerRole;
import com.ktb.moyeota.global.config.ServerRole;
import com.ktb.moyeota.global.event.EventEnvelope;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnServerRole({ServerRole.WORKER, ServerRole.ALL})
public class CongestionSignalConsumer {

    public static final String GROUP_ID = "congestion-signal-loader";

    private static final Set<String> SUPPORTED_EVENTS =
            Set.of(PostChangedMessages.POST_CREATED, PostChangedMessages.COMMENT_CREATED);

    private final CongestionSignalService signalService;

    @KafkaListener(topics = PostChangedMessages.TOPIC, groupId = GROUP_ID)
    public void consume(@Payload EventEnvelope<PostChangedData> envelope) {
        if (!SUPPORTED_EVENTS.contains(envelope.eventType())) {
            log.debug("[CONGESTION_SIGNAL_SKIPPED] eventId={}, eventType={}", envelope.eventId(), envelope.eventType());
            return;
        }
        envelope.requireSchemaVersion(PostChangedMessages.SCHEMA_VERSION);
        SignalLoadOutcome outcome = signalService.load(envelope.data());
        log.info("[CONGESTION_SIGNAL_LOADED] eventId={}, postId={}, outcome={}",
                envelope.eventId(), envelope.data().post().postId(), outcome);
    }
}
