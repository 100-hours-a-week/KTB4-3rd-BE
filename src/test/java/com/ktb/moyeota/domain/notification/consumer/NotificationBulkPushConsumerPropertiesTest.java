package com.ktb.moyeota.domain.notification.consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ktb.moyeota.domain.notification.consumer.NotificationBulkPushConsumerProperties.Type;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.BindException;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource;

class NotificationBulkPushConsumerPropertiesTest {

    @Test
    @DisplayName("설정이 없으면 한 건씩 받는 컨슈머를 쓰고 발송 스레드는 0이다")
    void defaultsToRecord() {
        NotificationBulkPushConsumerProperties properties = bind(Map.of());

        assertThat(properties.type()).isEqualTo(Type.RECORD);
        assertThat(properties.batchSendThreads()).isZero();
    }

    @Test
    @DisplayName("소문자로 써도 묶음 컨슈머를 고른다")
    void bindsLowerCaseType() {
        NotificationBulkPushConsumerProperties properties = bind(Map.of(
                NotificationBulkPushConsumerProperties.PREFIX + ".type", "batch",
                NotificationBulkPushConsumerProperties.PREFIX + ".batch-send-threads", "24"));

        assertThat(properties.type()).isEqualTo(Type.BATCH);
        assertThat(properties.batchSendThreads()).isEqualTo(24);
    }

    @Test
    @DisplayName("없는 컨슈머 이름을 쓰면 기동하지 않는다")
    void rejectsUnknownType() {
        assertThatThrownBy(() -> bind(Map.of(NotificationBulkPushConsumerProperties.PREFIX + ".type", "mutli")))
                .isInstanceOf(BindException.class);
    }

    @Test
    @DisplayName("발송 스레드가 0 미만이면 기동하지 않는다")
    void rejectsNegativeSendThreads() {
        assertThatThrownBy(() -> new NotificationBulkPushConsumerProperties(Type.BATCH, -1))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private static NotificationBulkPushConsumerProperties bind(Map<String, String> values) {
        return new Binder(new MapConfigurationPropertySource(values))
                .bindOrCreate(NotificationBulkPushConsumerProperties.PREFIX, NotificationBulkPushConsumerProperties.class);
    }
}
