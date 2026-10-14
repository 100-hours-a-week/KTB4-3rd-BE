package com.ktb.moyeota.domain.settlement.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties(prefix = "moyeota.settlement.transfer-analysis")
public record TransferAnalysisProperties(
        @DefaultValue("2") int poolSize,
        @DefaultValue("20") int queueCapacity,
        @DefaultValue("90s") Duration queueWaitLimit) {

    public TransferAnalysisProperties {
        if (poolSize < 1 || queueCapacity < 0) {
            throw new IllegalArgumentException("송금 인증 분석 풀 크기는 1 이상, 대기열은 0 이상이어야 합니다");
        }
    }
}
