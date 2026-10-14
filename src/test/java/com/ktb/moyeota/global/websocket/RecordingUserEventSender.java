package com.ktb.moyeota.global.websocket;

import java.util.concurrent.CopyOnWriteArrayList;
import java.util.List;

public class RecordingUserEventSender extends UserEventSender {

    private final List<Sent> sent = new CopyOnWriteArrayList<>();

    public RecordingUserEventSender() {
        super(null);
    }

    @Override
    public void send(Long userId, UserChannel channel, Object event) {
        sent.add(new Sent(userId, channel, event));
    }

    public List<Sent> sent() {
        return List.copyOf(sent);
    }

    public void clear() {
        sent.clear();
    }

    public record Sent(Long userId, UserChannel channel, Object event) {
    }
}
