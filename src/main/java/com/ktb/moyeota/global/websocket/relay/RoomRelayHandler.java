package com.ktb.moyeota.global.websocket.relay;

public interface RoomRelayHandler {

    void handle(Long roomId, RelayMessage message);
}
