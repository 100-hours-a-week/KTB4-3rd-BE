package com.ktb.moyeota.global.websocket.relay;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

public class RecordingWsRelayPublisher implements WsRelayPublisher {

    private final List<Published> published = new CopyOnWriteArrayList<>();

    @Override
    public void toRoom(Long roomId, String destination, Object payload) {
        published.add(new Published("room", roomId, destination, payload));
    }

    @Override
    public void toUser(Long userId, String destination, Object payload) {
        published.add(new Published("user", userId, destination, payload));
    }

    public List<Published> published() {
        return List.copyOf(published);
    }

    public void clear() {
        published.clear();
    }

    public record Published(String target, Long id, String destination, Object payload) {
    }
}
