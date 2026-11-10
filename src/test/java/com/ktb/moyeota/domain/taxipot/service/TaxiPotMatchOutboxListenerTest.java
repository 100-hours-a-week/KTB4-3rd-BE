package com.ktb.moyeota.domain.taxipot.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.ktb.moyeota.domain.companion.message.CompanionChangedMessages;
import com.ktb.moyeota.domain.taxipot.event.TaxiPotMatchedEvent;
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
import tools.jackson.databind.json.JsonMapper;

@DataJpaTest
@ImportAutoConfiguration(JacksonAutoConfiguration.class)
@Import({TaxiPotMatchOutboxListener.class, OutboxRecorder.class, EventEnvelopeFactory.class, TestClockConfig.class})
@TestPropertySource(properties = "moyeota.chat.system-message.mode=kafka")
class TaxiPotMatchOutboxListenerTest {

    @Autowired
    private ApplicationEventPublisher eventPublisher;

    @Autowired
    private OutboxEventRepository outboxEventRepository;

    @Autowired
    private JsonMapper jsonMapper;

    @Test
    @DisplayName("매칭되어 들어온 사람마다 매칭됨 사실을 택시팟 키로 남긴다")
    void recordsMatchedPerUser() {
        eventPublisher.publishEvent(new TaxiPotMatchedEvent(301L, 501L, List.of(7L, 8L)));

        List<OutboxEvent> recorded = outboxEventRepository.findAll().stream()
                .sorted(Comparator.comparing(OutboxEvent::getId))
                .toList();
        assertThat(recorded).hasSize(2).allSatisfy(event -> {
            assertThat(event.getTopic()).isEqualTo(CompanionChangedMessages.TOPIC);
            assertThat(event.getMessageKey()).isEqualTo("301");
            assertThat(event.getEventType()).isEqualTo(CompanionChangedMessages.MATCHED);
        });
        assertThat(recorded).extracting(event -> jsonMapper.readTree(event.getPayload()).get("user_id").asLong())
                .containsExactly(7L, 8L);
        assertThat(jsonMapper.readTree(recorded.get(0).getPayload()).get("kind").asString()).isEqualTo("TAXI_POT");
    }
}
