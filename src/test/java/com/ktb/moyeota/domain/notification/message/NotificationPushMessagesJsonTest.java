package com.ktb.moyeota.domain.notification.message;

import static org.assertj.core.api.Assertions.assertThat;

import com.ktb.moyeota.global.event.EventEnvelope;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.jackson.autoconfigure.JacksonAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

@SpringBootTest(classes = NotificationPushMessagesJsonTest.class)
@ImportAutoConfiguration(JacksonAutoConfiguration.class)
class NotificationPushMessagesJsonTest {

    @Autowired
    private JsonMapper jsonMapper;

    @Test
    @DisplayName("발송 요청 data 는 job_id 하나로 쓴다")
    void writesNotificationId() {
        JsonNode written = jsonMapper.valueToTree(new NotificationPushRequestData(42L));

        assertThat(written.propertyNames()).containsExactly("job_id");
        assertThat(written.get("job_id").asLong()).isEqualTo(42L);
    }

    @Test
    @DisplayName("발송 요청 메시지를 발송 작업 번호로 읽는다")
    void readsPushRequested() {
        String json = """
                {"event_id":"5b1d2c3e-4f50-4a61-8b72-9c83d4e5f601","event_type":"notification.push.requested",
                 "schema_version":1,"occurred_at":"2026-11-06T09:00:00.000+09:00",
                 "data":{"job_id":42}}
                """;

        EventEnvelope<NotificationPushRequestData> envelope = jsonMapper.readValue(json, new TypeReference<>() {
        });

        assertThat(envelope.eventType()).isEqualTo(NotificationPushMessages.PUSH_REQUESTED);
        assertThat(envelope.schemaVersion()).isEqualTo(NotificationPushMessages.SCHEMA_VERSION);
        assertThat(envelope.data().jobId()).isEqualTo(42L);
    }
}
