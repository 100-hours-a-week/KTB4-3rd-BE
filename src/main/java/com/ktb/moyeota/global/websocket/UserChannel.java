package com.ktb.moyeota.global.websocket;

public enum UserChannel {

    SETTLEMENT("/sub/settlement");

    private final String destination;

    UserChannel(String destination) {
        this.destination = destination;
    }

    public String destination() {
        return destination;
    }
}
