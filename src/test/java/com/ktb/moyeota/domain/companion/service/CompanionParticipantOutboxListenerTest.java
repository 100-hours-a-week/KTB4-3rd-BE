package com.ktb.moyeota.domain.companion.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import com.ktb.moyeota.domain.companion.entity.CompanionKind;
import com.ktb.moyeota.domain.companion.event.CompanionParticipantJoinedEvent;
import com.ktb.moyeota.domain.companion.event.CompanionParticipantLeftEvent;
import com.ktb.moyeota.domain.companion.message.CompanionChangedMessages;
import com.ktb.moyeota.global.config.TestClockConfig;
import com.ktb.moyeota.global.event.EventEnvelopeFactory;
import com.ktb.moyeota.global.outbox.entity.OutboxEvent;
import com.ktb.moyeota.global.outbox.repository.OutboxEventRepository;
import com.ktb.moyeota.global.outbox.service.OutboxRecorder;
import java.util.Comparator;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jackson.autoconfigure.JacksonAutoConfiguration;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

@DataJpaTest
@ImportAutoConfiguration(JacksonAutoConfiguration.class)
@Import({CompanionParticipantOutboxListener.class, OutboxRecorder.class, EventEnvelopeFactory.class,
        TestClockConfig.class})
@TestPropertySource(properties = "moyeota.chat.system-message.mode=kafka")
class CompanionParticipantOutboxListenerTest {

    @Autowired
    private ApplicationEventPublisher eventPublisher;

    @Autowired
    private OutboxEventRepository outboxEventRepository;

    @Autowired
    private JsonMapper jsonMapper;

    @Test
    @DisplayName("입장 · 퇴장을 동행 바뀜 토픽에 동행 키로, 들어오고 나간 사람과 함께 남긴다")
    void recordsJoinedAndLeft() {
        eventPublisher.publishEvent(new CompanionParticipantJoinedEvent(201L, CompanionKind.COMPANION, 7L));
        eventPublisher.publishEvent(new CompanionParticipantLeftEvent(201L, CompanionKind.COMPANION, 7L));

        List<OutboxEvent> recorded = outboxEventRepository.findAll().stream()
                .sorted(Comparator.comparing(OutboxEvent::getId))
                .toList();
        assertThat(recorded).extracting(OutboxEvent::getTopic, OutboxEvent::getMessageKey, OutboxEvent::getEventType)
                .containsExactly(
                        tuple(CompanionChangedMessages.TOPIC, "201", CompanionChangedMessages.PARTICIPANT_JOINED),
                        tuple(CompanionChangedMessages.TOPIC, "201", CompanionChangedMessages.PARTICIPANT_LEFT));
        JsonNode data = jsonMapper.readTree(recorded.get(1).getPayload());
        assertThat(data.get("companion_id").asLong()).isEqualTo(201L);
        assertThat(data.get("kind").asString()).isEqualTo("COMPANION");
        assertThat(data.get("user_id").asLong()).isEqualTo(7L);
    }
}
