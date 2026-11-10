package com.ktb.moyeota.domain.taxipot.service;

import com.ktb.moyeota.domain.chat.entity.ChatRoom;
import com.ktb.moyeota.domain.chat.repository.ChatRoomRepository;
import com.ktb.moyeota.domain.chat.service.ChatSystemMessageService;
import com.ktb.moyeota.domain.taxipot.event.TaxiPotRideEndedEvent;
import com.ktb.moyeota.domain.taxipot.event.TaxiPotRideStartedEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "moyeota.chat.system-message", name = "mode", havingValue = "direct",
        matchIfMissing = true)
public class TaxiPotRideChatMessageListener {

    private final ChatRoomRepository chatRoomRepository;
    private final ChatSystemMessageService chatSystemMessageService;

    @EventListener
    public void onRideStarted(TaxiPotRideStartedEvent event) {
        chatSystemMessageService.rideStarted(findChatRoom(event.taxiPotId()));
    }

    @EventListener
    public void onRideEnded(TaxiPotRideEndedEvent event) {
        ChatRoom chatRoom = findChatRoom(event.taxiPotId());
        chatSystemMessageService.rideEnded(chatRoom);
        chatSystemMessageService.requestMeter(chatRoom);
    }

    private ChatRoom findChatRoom(Long taxiPotId) {
        return chatRoomRepository.findByCompanionId(taxiPotId)
                .orElseThrow(() -> new IllegalStateException("택시팟에 채팅방이 없다: " + taxiPotId));
    }
}
