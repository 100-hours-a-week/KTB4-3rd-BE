package com.ktb.moyeota.domain.chat.service;

import com.ktb.moyeota.domain.chat.entity.MessageType;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

final class SystemMessageKeys {

    private SystemMessageKeys() {
    }

    static String forCompanion(Long companionId, MessageType messageType) {
        return of("companion:" + companionId, messageType);
    }

    static String of(String eventId, MessageType messageType) {
        return UUID.nameUUIDFromBytes((eventId + ":" + messageType.name()).getBytes(StandardCharsets.UTF_8))
                .toString();
    }
}
