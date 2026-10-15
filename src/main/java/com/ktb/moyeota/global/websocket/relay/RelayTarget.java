package com.ktb.moyeota.global.websocket.relay;

import java.util.Arrays;
import java.util.Optional;

public record RelayTarget(Kind kind, Long id) {

    public enum Kind {

        ROOM("room"),
        USER("user");

        private final String key;

        Kind(String key) {
            this.key = key;
        }

        public String key() {
            return key;
        }

        static Optional<Kind> fromKey(String key) {
            return Arrays.stream(values()).filter(kind -> kind.key.equals(key)).findFirst();
        }
    }
}
