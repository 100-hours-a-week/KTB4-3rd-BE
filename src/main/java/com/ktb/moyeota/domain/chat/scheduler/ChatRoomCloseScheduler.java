package com.ktb.moyeota.domain.chat.scheduler;

import com.ktb.moyeota.domain.chat.service.ChatRoomCloseService;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ChatRoomCloseScheduler {

    private final ChatRoomCloseService chatRoomCloseService;

    @Scheduled(fixedDelayString = "${moyeota.chat.close-sweep-delay}")
    public void sweep() {
        chatRoomCloseService.closeAfterRide();
    }
}
