package com.ktb.moyeota.domain.carpool.service;

import com.ktb.moyeota.domain.carpool.event.CarpoolRideEndedEvent;
import com.ktb.moyeota.domain.carpool.event.CarpoolRideStartedEvent;
import com.ktb.moyeota.domain.chat.entity.ChatRoom;
import com.ktb.moyeota.domain.chat.repository.ChatRoomRepository;
import com.ktb.moyeota.domain.chat.service.ChatSystemMessageService;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "moyeota.chat.system-message", name = "mode", havingValue = "direct",
        matchIfMissing = true)
public class CarpoolRideChatMessageListener {

    private final ChatRoomRepository chatRoomRepository;
    private final ChatSystemMessageService chatSystemMessageService;

    @EventListener
    public void onRideStarted(CarpoolRideStartedEvent event) {
        chatSystemMessageService.rideStarted(findChatRoom(event.carpoolId()));
    }

    @EventListener
    public void onRideEnded(CarpoolRideEndedEvent event) {
        chatSystemMessageService.rideEnded(findChatRoom(event.carpoolId()));
    }

    private ChatRoom findChatRoom(Long carpoolId) {
        return chatRoomRepository.findByCompanionId(carpoolId)
                .orElseThrow(() -> new IllegalStateException("카풀에 채팅방이 없다: " + carpoolId));
    }
}
