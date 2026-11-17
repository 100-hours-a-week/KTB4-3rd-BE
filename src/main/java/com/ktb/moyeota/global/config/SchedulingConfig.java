package com.ktb.moyeota.global.config;

import org.springframework.boot.task.ThreadPoolTaskSchedulerBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

@Configuration
@EnableScheduling
@ConditionalOnServerRole({ServerRole.WORKER, ServerRole.ALL})
public class SchedulingConfig {

    public static final String OUTBOX_PUBLISH_SCHEDULER = "outboxPublishTaskScheduler";

    @Bean
    public ThreadPoolTaskScheduler taskScheduler(ThreadPoolTaskSchedulerBuilder builder) {
        return builder.build();
    }

    @Bean(OUTBOX_PUBLISH_SCHEDULER)
    public ThreadPoolTaskScheduler outboxPublishTaskScheduler(ThreadPoolTaskSchedulerBuilder builder) {
        return builder.poolSize(1).threadNamePrefix("outbox-publish-").build();
    }
}
