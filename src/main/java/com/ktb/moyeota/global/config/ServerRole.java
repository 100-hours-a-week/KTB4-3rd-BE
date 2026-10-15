package com.ktb.moyeota.global.config;

public enum ServerRole {

    REST,
    WEBSOCKET,
    ALL;

    public static final String ENV_NAME = "SERVICE_NAME";

    public static ServerRole from(String value) {
        if (value == null || value.isBlank()) {
            return ALL;
        }
        return switch (value.trim()) {
            case "rest" -> REST;
            case "websocket" -> WEBSOCKET;
            case "all" -> ALL;
            default -> throw new IllegalStateException("SERVICE_NAME은 rest, websocket, all 중 하나여야 합니다.");
        };
    }

    public boolean acceptsWebSocket() {
        return this == WEBSOCKET || this == ALL;
    }
}
