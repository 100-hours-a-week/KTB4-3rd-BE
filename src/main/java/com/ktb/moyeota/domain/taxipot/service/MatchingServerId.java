package com.ktb.moyeota.domain.taxipot.service;

import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class MatchingServerId {

    private static final int RANDOM_LENGTH = 8;

    private final String value;

    public MatchingServerId() {
        this(System.getenv().getOrDefault("HOSTNAME", "local"));
    }

    MatchingServerId(String hostName) {
        this.value = hostName + "-" + UUID.randomUUID().toString().substring(0, RANDOM_LENGTH);
    }

    public String value() {
        return value;
    }
}
