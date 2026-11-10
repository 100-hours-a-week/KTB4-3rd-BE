package com.ktb.moyeota.global.websocket.subscription;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties(prefix = "moyeota.internal")
public record InternalApiProperties(
        String apiKey,
        @DefaultValue("http://rest:8080") String restBaseUrl,
        @DefaultValue("1s") Duration connectTimeout,
        @DefaultValue("2s") Duration readTimeout) {
}
