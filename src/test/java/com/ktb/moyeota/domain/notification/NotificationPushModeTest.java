package com.ktb.moyeota.domain.notification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.ktb.moyeota.domain.notification.scheduler.NotificationPushScheduler;
import com.ktb.moyeota.domain.notification.service.NotificationPushEventListener;
import com.ktb.moyeota.domain.notification.service.NotificationPushService;
import com.ktb.moyeota.global.config.NotificationPushConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.core.task.SyncTaskExecutor;
import org.springframework.core.task.TaskExecutor;

class NotificationPushModeTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withBean(NotificationPushService.class, () -> mock(NotificationPushService.class))
            .withBean(NotificationPushConfig.PUSH_EXECUTOR, TaskExecutor.class, SyncTaskExecutor::new)
            .withUserConfiguration(NotificationPushEventListener.class, NotificationPushScheduler.class);

    @Test
    @DisplayName("mode 를 정하지 않으면 rdb 로 즉시 발송 리스너와 재시도 스윕이 켜진다")
    void rdbByDefault() {
        runner.run(context -> {
            assertThat(context).hasSingleBean(NotificationPushEventListener.class);
            assertThat(context).hasSingleBean(NotificationPushScheduler.class);
        });
    }

    @Test
    @DisplayName("kafka 모드면 즉시 발송 리스너와 재시도 스윕이 꺼진다")
    void kafkaTurnsOffRdbPath() {
        runner.withPropertyValues("moyeota.notification.push.mode=kafka").run(context -> {
            assertThat(context).doesNotHaveBean(NotificationPushEventListener.class);
            assertThat(context).doesNotHaveBean(NotificationPushScheduler.class);
        });
    }
}
