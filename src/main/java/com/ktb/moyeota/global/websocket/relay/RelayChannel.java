package com.ktb.moyeota.global.websocket.relay;

import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class RelayChannel {

    private static final String VERSION = "v1";
    private static final String SEPARATOR = ":";

    private final RelayProperties relayProperties;

    public String room(Long roomId) {
        return channel(RelayTarget.Kind.ROOM, roomId);
    }

    public String user(Long userId) {
        return channel(RelayTarget.Kind.USER, userId);
    }

    public String pattern() {
        return prefix() + "*";
    }

    public Optional<RelayTarget> parse(String channel) {
        String prefix = prefix();
        if (channel == null || !channel.startsWith(prefix)) {
            return Optional.empty();
        }
        String[] parts = channel.substring(prefix.length()).split(SEPARATOR, -1);
        if (parts.length != 2) {
            return Optional.empty();
        }
        return RelayTarget.Kind.fromKey(parts[0])
                .flatMap(kind -> parseId(parts[1]).map(id -> new RelayTarget(kind, id)));
    }

    private String channel(RelayTarget.Kind kind, Long id) {
        return prefix() + kind.key() + SEPARATOR + id;
    }

    private String prefix() {
        return "moyeota" + SEPARATOR + relayProperties.env() + SEPARATOR + "ws" + SEPARATOR + VERSION + SEPARATOR;
    }

    private static Optional<Long> parseId(String value) {
        try {
            return Optional.of(Long.valueOf(value));
        } catch (NumberFormatException e) {
            return Optional.empty();
        }
    }
}
