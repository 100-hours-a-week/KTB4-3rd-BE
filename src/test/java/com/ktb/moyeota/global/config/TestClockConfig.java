package com.ktb.moyeota.global.config;

import java.time.Instant;
import java.time.ZoneId;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;

@TestConfiguration(proxyBeanMethods = false)
public class TestClockConfig {

    public static final ZoneId ZONE = ZoneId.of("Asia/Seoul");
    public static final Instant DEFAULT_INSTANT = Instant.parse("2026-01-01T00:00:00Z");

    @Bean
    MutableClock clock() {
        return new MutableClock(DEFAULT_INSTANT, ZONE);
    }
}
