package com.ktb.moyeota.domain.chat.service;

import com.ktb.moyeota.domain.chat.entity.ChatRoom;
import com.ktb.moyeota.domain.chat.repository.ChatRoomRepository;
import com.ktb.moyeota.domain.companion.event.CompanionParticipantJoinedEvent;
import com.ktb.moyeota.domain.companion.event.CompanionParticipantLeftEvent;
import com.ktb.moyeota.domain.user.entity.User;
import com.ktb.moyeota.domain.user.repository.UserRepository;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "moyeota.chat.system-message", name = "mode", havingValue = "direct",
        matchIfMissing = true)
public class CompanionParticipantChatMessageListener {

    private final ChatRoomRepository chatRoomRepository;
    private final UserRepository userRepository;
    private final ChatSystemMessageService chatSystemMessageService;

    @EventListener
    public void onJoined(CompanionParticipantJoinedEvent event) {
        openChatRoom(event.companionId())
                .ifPresent(chatRoom -> chatSystemMessageService.enter(chatRoom, user(event.userId())));
    }

    @EventListener
    public void onLeft(CompanionParticipantLeftEvent event) {
        openChatRoom(event.companionId())
                .ifPresent(chatRoom -> chatSystemMessageService.leave(chatRoom, user(event.userId())));
    }

    private Optional<ChatRoom> openChatRoom(Long companionId) {
        return chatRoomRepository.findByCompanionId(companionId)
                .filter(chatRoom -> chatRoom.getClosedAt() == null);
    }

    private User user(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new IllegalStateException("사용자가 없다: " + userId));
    }
}
