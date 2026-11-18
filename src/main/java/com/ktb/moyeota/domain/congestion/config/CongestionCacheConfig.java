package com.ktb.moyeota.domain.congestion.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

@Configuration
public class CongestionCacheConfig {

    public static final String RELOAD_EXECUTOR = "congestionCacheReloadExecutor";

    @Bean(name = RELOAD_EXECUTOR)
    public ThreadPoolTaskExecutor congestionCacheReloadExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setMaxPoolSize(1);
        executor.setQueueCapacity(0);
        executor.setThreadNamePrefix("congestion-cache-reload-");
        return executor;
    }
}
