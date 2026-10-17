package com.ktb.moyeota.global.websocket.subscription;

public interface ChatRoomParticipantChecker {

    boolean isParticipant(Long roomId, Long userId);
}
