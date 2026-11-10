package com.ktb.moyeota.domain.carpool.service;

import static com.ktb.moyeota.domain.companion.entity.CompanionStatus.COMPLETED;
import static com.ktb.moyeota.domain.companion.entity.CompanionStatus.IN_PROGRESS;
import static com.ktb.moyeota.domain.companion.entity.CompanionStatus.RECRUITING;
import static org.assertj.core.api.Assertions.assertThat;

import com.ktb.moyeota.domain.companion.entity.Companion;
import com.ktb.moyeota.domain.companion.message.CompanionChangedMessages;
import com.ktb.moyeota.global.event.EventEnvelopeFactory;
import com.ktb.moyeota.global.outbox.entity.OutboxEvent;
import com.ktb.moyeota.global.outbox.repository.OutboxEventRepository;
import com.ktb.moyeota.global.outbox.service.OutboxRecorder;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.jackson.autoconfigure.JacksonAutoConfiguration;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

@ImportAutoConfiguration(JacksonAutoConfiguration.class)
@Import({CarpoolRideOutboxListener.class, OutboxRecorder.class, EventEnvelopeFactory.class})
@TestPropertySource(properties = "moyeota.chat.system-message.mode=kafka")
class CarpoolRideOutboxListenerTest extends CarpoolRideTestSupport {

    @Autowired
    private OutboxEventRepository outboxEventRepository;

    @Autowired
    private JsonMapper jsonMapper;

    @Test
    @DisplayName("운행을 시작하면 채팅 메시지 대신 동행 바뀜 토픽에 카풀 운행 시작 사실을 남긴다")
    void recordsRideStarted() {
        Companion carpool = persistCarpool(RECRUITING, 2);

        carpoolRideService.changeStatus(carpool.getHost().getId(), carpool.getId(), IN_PROGRESS);

        OutboxEvent recorded = onlyRecorded();
        assertThat(recorded.getTopic()).isEqualTo(CompanionChangedMessages.TOPIC);
        assertThat(recorded.getMessageKey()).isEqualTo(String.valueOf(carpool.getId()));
        assertThat(recorded.getEventType()).isEqualTo(CompanionChangedMessages.RIDE_STARTED);
        JsonNode data = jsonMapper.readTree(recorded.getPayload());
        assertThat(data.get("companion_id").asLong()).isEqualTo(carpool.getId());
        assertThat(data.get("kind").asString()).isEqualTo("CARPOOL");
        assertThat(messageTypes(carpool)).isEmpty();
    }

    @Test
    @DisplayName("운행을 종료하면 채팅 메시지 대신 카풀 운행 종료 사실을 남긴다")
    void recordsRideEnded() {
        Companion carpool = persistCarpool(IN_PROGRESS, 2);

        carpoolRideService.changeStatus(carpool.getHost().getId(), carpool.getId(), COMPLETED);

        OutboxEvent recorded = onlyRecorded();
        assertThat(recorded.getEventType()).isEqualTo(CompanionChangedMessages.RIDE_ENDED);
        assertThat(messageTypes(carpool)).isEmpty();
    }

    private OutboxEvent onlyRecorded() {
        entityManager.flush();
        List<OutboxEvent> recorded = outboxEventRepository.findAll();
        assertThat(recorded).hasSize(1);
        return recorded.get(0);
    }
}
