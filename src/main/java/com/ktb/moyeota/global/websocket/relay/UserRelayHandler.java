package com.ktb.moyeota.global.websocket.relay;

public interface UserRelayHandler {

    void handle(Long userId, RelayMessage message);
}
