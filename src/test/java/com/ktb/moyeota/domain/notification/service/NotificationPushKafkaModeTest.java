package com.ktb.moyeota.domain.notification.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.ktb.moyeota.domain.notification.scheduler.NotificationPushRepublishScheduler;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class NotificationPushKafkaModeTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withBean(NotificationPushPublisher.class, () -> mock(NotificationPushPublisher.class))
            .withBean(NotificationPushRepublisher.class, () -> mock(NotificationPushRepublisher.class))
            .withUserConfiguration(NotificationPushKafkaEventListener.class, NotificationPushRepublishScheduler.class);

    @Test
    @DisplayName("mode 를 정하지 않으면 rdb 라 커밋 직후 발행과 재발행 스윕이 꺼져 있다")
    void offByDefault() {
        runner.run(context -> {
            assertThat(context).doesNotHaveBean(NotificationPushKafkaEventListener.class);
            assertThat(context).doesNotHaveBean(NotificationPushRepublishScheduler.class);
        });
    }

    @Test
    @DisplayName("kafka 모드면 커밋 직후 발행과 재발행 스윕이 켜진다")
    void onInKafkaMode() {
        runner.withPropertyValues("moyeota.notification.push.mode=kafka").run(context -> {
            assertThat(context).hasSingleBean(NotificationPushKafkaEventListener.class);
            assertThat(context).hasSingleBean(NotificationPushRepublishScheduler.class);
        });
    }
}
