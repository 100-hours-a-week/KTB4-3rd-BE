package com.ktb.moyeota.domain.notification.consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ktb.moyeota.domain.notification.consumer.NotificationPushConsumerProperties.Type;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.BindException;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource;

class NotificationPushConsumerPropertiesTest {

    @Test
    @DisplayName("설정이 없으면 한 건씩 받는 리스너 컨슈머를 쓴다")
    void defaultsToRecord() {
        assertThat(bind(Map.of()).type()).isEqualTo(Type.RECORD);
    }

    @Test
    @DisplayName("Parallel Consumer 를 고를 수 있다")
    void bindsParallelType() {
        NotificationPushConsumerProperties properties =
                bind(Map.of(NotificationPushConsumerProperties.PREFIX + ".type", "parallel"));

        assertThat(properties.type()).isEqualTo(Type.PARALLEL);
    }

    @Test
    @DisplayName("없는 컨슈머 이름을 쓰면 기동하지 않는다")
    void rejectsUnknownType() {
        assertThatThrownBy(() -> bind(Map.of(NotificationPushConsumerProperties.PREFIX + ".type", "paralel")))
                .isInstanceOf(BindException.class);
    }

    private static NotificationPushConsumerProperties bind(Map<String, String> values) {
        return new Binder(new MapConfigurationPropertySource(values))
                .bindOrCreate(NotificationPushConsumerProperties.PREFIX, NotificationPushConsumerProperties.class);
    }
}
