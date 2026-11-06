package com.ktb.moyeota.global.config;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Set;

public enum ServerRole {

    REST,
    WEBSOCKET,
    MATCHING,
    WORKER,
    ALL;

    public static final String ENV_NAME = "APP_ROLE";

    private static final String SEPARATOR = ",";
    private static final Set<ServerRole> ALONE = EnumSet.of(WEBSOCKET, ALL);

    public static Set<ServerRole> from(String value) {
        if (value == null || value.isBlank()) {
            return Collections.unmodifiableSet(EnumSet.of(ALL));
        }
        Set<ServerRole> roles = EnumSet.noneOf(ServerRole.class);
        for (String name : value.split(SEPARATOR, -1)) {
            roles.add(parse(name.trim()));
        }
        if (roles.size() > 1 && roles.stream().anyMatch(ALONE::contains)) {
            throw new IllegalStateException("APP_ROLE의 websocket과 all은 다른 역할과 함께 쓸 수 없습니다.");
        }
        return Collections.unmodifiableSet(roles);
    }

    private static ServerRole parse(String name) {
        return switch (name) {
            case "rest" -> REST;
            case "websocket" -> WEBSOCKET;
            case "matching" -> MATCHING;
            case "worker" -> WORKER;
            case "all" -> ALL;
            default -> throw new IllegalStateException(
                    "APP_ROLE은 rest, websocket, matching, worker, all 중에서 쉼표로 골라야 합니다.");
        };
    }

    public boolean acceptsWebSocket() {
        return this == WEBSOCKET || this == ALL;
    }
}
