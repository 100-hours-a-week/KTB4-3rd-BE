package com.ktb.moyeota.global.websocket.subscription;

import com.ktb.moyeota.domain.chat.service.ChatRoomService;
import com.ktb.moyeota.global.config.ConditionalOnServerRole;
import com.ktb.moyeota.global.config.ServerRole;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnServerRole({ServerRole.REST, ServerRole.WORKER, ServerRole.ALL})
@RequiredArgsConstructor
public class LocalChatRoomParticipantChecker implements ChatRoomParticipantChecker {

    private final ChatRoomService chatRoomService;

    @Override
    public boolean isParticipant(Long roomId, Long userId) {
        return chatRoomService.isActiveParticipant(roomId, userId);
    }
}
