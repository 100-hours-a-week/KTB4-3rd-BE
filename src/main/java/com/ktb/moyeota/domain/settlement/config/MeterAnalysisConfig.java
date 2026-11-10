package com.ktb.moyeota.domain.settlement.config;

import com.ktb.moyeota.global.external.ai.AiProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

@Configuration
public class MeterAnalysisConfig {

    public static final String METER_ANALYSIS_EXECUTOR = "meterAnalysisExecutor";

    private static final long SHUTDOWN_MARGIN_SECONDS = 5;

    @Bean(name = METER_ANALYSIS_EXECUTOR)
    public ThreadPoolTaskExecutor meterAnalysisExecutor(MeterAnalysisProperties properties, AiProperties aiProperties) {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(properties.poolSize());
        executor.setMaxPoolSize(properties.poolSize());
        executor.setQueueCapacity(properties.queueCapacity());
        executor.setThreadNamePrefix("meter-analysis-");
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(
                Math.toIntExact(aiProperties.readTimeout().toSeconds() + SHUTDOWN_MARGIN_SECONDS));
        return executor;
    }
}
