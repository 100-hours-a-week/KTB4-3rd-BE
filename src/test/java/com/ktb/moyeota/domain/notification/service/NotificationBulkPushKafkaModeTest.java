package com.ktb.moyeota.domain.notification.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class NotificationBulkPushKafkaModeTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withBean(NotificationPushPublisher.class, () -> mock(NotificationPushPublisher.class))
            .withUserConfiguration(NotificationBulkPushKafkaEventListener.class);

    @Test
    @DisplayName("mode 를 정하지 않으면 rdb 라 리마인드 커밋 뒤 발행이 꺼져 있다")
    void offByDefault() {
        runner.run(context -> assertThat(context).doesNotHaveBean(NotificationBulkPushKafkaEventListener.class));
    }

    @Test
    @DisplayName("kafka 모드면 리마인드 커밋 뒤 발행이 켜진다")
    void onInKafkaMode() {
        runner.withPropertyValues("moyeota.notification.push.mode=kafka")
                .run(context -> assertThat(context).hasSingleBean(NotificationBulkPushKafkaEventListener.class));
    }
}
