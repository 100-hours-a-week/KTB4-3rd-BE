package com.ktb.moyeota.global.event;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.OffsetDateTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.jackson.autoconfigure.JacksonAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

@SpringBootTest(classes = EventEnvelopeJsonTest.class)
@ImportAutoConfiguration(JacksonAutoConfiguration.class)
class EventEnvelopeJsonTest {

    @Autowired
    private JsonMapper jsonMapper;

    @Test
    @DisplayName("봉투는 event_id · event_type · schema_version · occurred_at 과 data 로 나간다")
    void writesEnvelopeFields() {
        EventEnvelope<RideEnded> envelope = new EventEnvelope<>(
                "0e9f6c1a-3b7d-4f7e-9a51-6f2c8d1e4b90", "ride.ended", 1,
                OffsetDateTime.parse("2026-10-28T18:42:10+09:00"), new RideEnded(55L, "TAXI_POT"));

        JsonNode json = jsonMapper.readTree(jsonMapper.writeValueAsString(envelope));

        assertThat(json.propertyNames()).containsExactly(
                "event_id", "event_type", "schema_version", "occurred_at", "data");
        assertThat(json.get("event_type").asString()).isEqualTo("ride.ended");
        assertThat(json.get("schema_version").asInt()).isEqualTo(1);
        assertThat(OffsetDateTime.parse(json.get("occurred_at").asString()))
                .isAtSameInstantAs(OffsetDateTime.parse("2026-10-28T18:42:10+09:00"));
        assertThat(json.get("data").get("companion_id").asLong()).isEqualTo(55L);
    }

    @Test
    @DisplayName("다른 서버가 보낸 봉투 JSON 을 내용 타입까지 읽고, 모르는 필드는 무시한다")
    void readsEnvelopeWithData() {
        String json = """
                {"event_id":"0e9f6c1a-3b7d-4f7e-9a51-6f2c8d1e4b90","event_type":"ride.ended",
                 "schema_version":1,"occurred_at":"2026-10-28T09:42:10Z",
                 "data":{"companion_id":55,"kind":"TAXI_POT","added_later":true}}
                """;

        EventEnvelope<RideEnded> envelope = jsonMapper.readValue(json, new TypeReference<>() {
        });

        assertThat(envelope.eventId()).isEqualTo("0e9f6c1a-3b7d-4f7e-9a51-6f2c8d1e4b90");
        assertThat(envelope.occurredAt()).isAtSameInstantAs(OffsetDateTime.parse("2026-10-28T18:42:10+09:00"));
        assertThat(envelope.data()).isEqualTo(new RideEnded(55L, "TAXI_POT"));
    }

    record RideEnded(Long companionId, String kind) {
    }
}
