package com.ktb.moyeota.global.websocket.relay;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class RelayChannel {

    private static final String VERSION = "v1";

    private final RelayProperties relayProperties;

    public String room(Long roomId) {
        return prefix() + "room:" + roomId;
    }

    public String user(Long userId) {
        return prefix() + "user:" + userId;
    }

    private String prefix() {
        return "moyeota:" + relayProperties.env() + ":ws:" + VERSION + ":";
    }
}
