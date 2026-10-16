package com.ktb.moyeota.global.websocket.relay;

import java.util.Set;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "moyeota.relay")
public record RelayProperties(String env) {

    private static final Set<String> SUPPORTED_ENVS = Set.of("local", "dev", "stg", "prod");

    public RelayProperties {
        if (env == null || !SUPPORTED_ENVS.contains(env)) {
            throw new IllegalStateException("moyeota.relay.env는 local, dev, stg, prod 중 하나여야 합니다.");
        }
    }
}
