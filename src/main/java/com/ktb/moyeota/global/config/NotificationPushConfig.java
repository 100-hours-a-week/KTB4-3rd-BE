package com.ktb.moyeota.global.config;

import com.ktb.moyeota.domain.notification.push.LoggingPushSender;
import com.ktb.moyeota.domain.notification.push.PushSender;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

@Configuration
public class NotificationPushConfig {

    public static final String PUSH_EXECUTOR = "notificationPushExecutor";

    @Bean(name = PUSH_EXECUTOR)
    public ThreadPoolTaskExecutor notificationPushExecutor(NotificationPushProperties properties) {
        NotificationPushProperties.Executor executor = properties.executor();
        ThreadPoolTaskExecutor taskExecutor = new ThreadPoolTaskExecutor();
        taskExecutor.setCorePoolSize(executor.corePoolSize());
        taskExecutor.setMaxPoolSize(executor.maxPoolSize());
        taskExecutor.setQueueCapacity(executor.queueCapacity());
        taskExecutor.setThreadNamePrefix("notification-push-");
        taskExecutor.setWaitForTasksToCompleteOnShutdown(true);
        taskExecutor.setAwaitTerminationSeconds(30);
        return taskExecutor;
    }

    @Bean
    @ConditionalOnProperty(prefix = "moyeota.notification.push", name = "enabled",
            havingValue = "false", matchIfMissing = true)
    public PushSender loggingPushSender() {
        return new LoggingPushSender();
    }
}
