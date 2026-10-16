package com.ktb.moyeota.global.websocket.relay;

public interface WsRelayPublisher {

    void toRoom(Long roomId, String destination, Object payload);

    void toUser(Long userId, String destination, Object payload);
}
