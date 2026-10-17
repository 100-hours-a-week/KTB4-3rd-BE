package com.ktb.moyeota.domain.settlement.config;

import com.ktb.moyeota.global.external.ai.AiProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

@Configuration
public class TransferAnalysisConfig {

    public static final String TRANSFER_ANALYSIS_EXECUTOR = "transferAnalysisExecutor";

    private static final long SHUTDOWN_MARGIN_SECONDS = 5;

    @Bean(name = TRANSFER_ANALYSIS_EXECUTOR)
    public ThreadPoolTaskExecutor transferAnalysisExecutor(
            TransferAnalysisProperties properties, AiProperties aiProperties) {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(properties.poolSize());
        executor.setMaxPoolSize(properties.poolSize());
        executor.setQueueCapacity(properties.queueCapacity());
        executor.setThreadNamePrefix("transfer-analysis-");
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(
                Math.toIntExact(aiProperties.readTimeout().toSeconds() + SHUTDOWN_MARGIN_SECONDS));
        return executor;
    }
}
