package com.ktb.moyeota.global.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.ktb.moyeota.domain.notification.push.LoggingPushSender;
import com.ktb.moyeota.domain.notification.push.PushSender;
import java.time.Duration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

class NotificationPushConfigTest {

    @Configuration
    @EnableConfigurationProperties(NotificationPushProperties.class)
    @Import(NotificationPushConfig.class)
    static class TestConfig {
    }

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(TestConfig.class);

    @Test
    @DisplayName("설정하지 않으면 기본값을 쓴다")
    void defaults() {
        runner.run(context -> {
            NotificationPushProperties properties = context.getBean(NotificationPushProperties.class);

            assertThat(properties.enabled()).isFalse();
            assertThat(properties.sweepDelay()).isEqualTo(Duration.ofSeconds(10));
            assertThat(properties.batchSize()).isEqualTo(50);
            assertThat(properties.lease()).isEqualTo(Duration.ofSeconds(60));
            assertThat(properties.retryDelay()).isEqualTo(Duration.ofSeconds(30));
            assertThat(properties.maxAttempts()).isEqualTo(3);
            assertThat(properties.fcm().connectTimeout()).isEqualTo(Duration.ofSeconds(10));
            assertThat(properties.fcm().readTimeout()).isEqualTo(Duration.ofSeconds(10));
        });
    }

    @Test
    @DisplayName("설정 값으로 덮어쓸 수 있다")
    void override() {
        runner.withPropertyValues(
                        "moyeota.notification.push.batch-size=10",
                        "moyeota.notification.push.max-attempts=5",
                        "moyeota.notification.push.retry-delay=1m")
                .run(context -> {
                    NotificationPushProperties properties = context.getBean(NotificationPushProperties.class);

                    assertThat(properties.batchSize()).isEqualTo(10);
                    assertThat(properties.maxAttempts()).isEqualTo(5);
                    assertThat(properties.retryDelay()).isEqualTo(Duration.ofMinutes(1));
                });
    }

    @Test
    @DisplayName("선점 시간이 FCM 타임아웃 합보다 짧으면 기동에 실패한다")
    void leaseMustExceedFcmTimeout() {
        runner.withPropertyValues("moyeota.notification.push.lease=15s")
                .run(context -> assertThat(context).hasFailed());
    }

    @Test
    @DisplayName("발송 전용 스레드 풀을 설정 값대로 만든다")
    void executor() {
        runner.run(context -> {
            ThreadPoolTaskExecutor executor = context.getBean(
                    NotificationPushConfig.PUSH_EXECUTOR, ThreadPoolTaskExecutor.class);

            assertThat(executor.getCorePoolSize()).isEqualTo(2);
            assertThat(executor.getMaxPoolSize()).isEqualTo(4);
            assertThat(executor.getQueueCapacity()).isEqualTo(100);
            assertThat(executor.getThreadNamePrefix()).isEqualTo("notification-push-");
        });
    }

    @Test
    @DisplayName("enabled가 false(기본값)면 로그 전용 발송기를 쓴다")
    void loggingSenderWhenDisabled() {
        runner.run(context -> assertThat(context.getBean(PushSender.class)).isInstanceOf(LoggingPushSender.class));
    }

    @Test
    @DisplayName("enabled가 true면 로그 전용 발송기를 등록하지 않는다")
    void noLoggingSenderWhenEnabled() {
        runner.withPropertyValues("moyeota.notification.push.enabled=true")
                .run(context -> assertThat(context).doesNotHaveBean(LoggingPushSender.class));
    }
}
