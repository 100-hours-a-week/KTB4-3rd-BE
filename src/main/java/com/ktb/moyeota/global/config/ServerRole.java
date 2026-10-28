package com.ktb.moyeota.global.config;

public enum ServerRole {

    REST,
    WEBSOCKET,
    WORKER,
    ALL;

    public static final String ENV_NAME = "APP_ROLE";

    public static ServerRole from(String value) {
        if (value == null || value.isBlank()) {
            return ALL;
        }
        return switch (value.trim()) {
            case "rest" -> REST;
            case "websocket" -> WEBSOCKET;
            case "worker" -> WORKER;
            case "all" -> ALL;
            default -> throw new IllegalStateException("APP_ROLE은 rest, websocket, worker, all 중 하나여야 합니다.");
        };
    }

    public boolean acceptsWebSocket() {
        return this == WEBSOCKET || this == ALL;
    }
}
