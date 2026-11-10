package com.ktb.moyeota.domain.chat.scheduler;

import com.ktb.moyeota.domain.chat.service.ChatRoomCloseService;
import com.ktb.moyeota.global.config.ConditionalOnServerRole;
import com.ktb.moyeota.global.config.ServerRole;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@ConditionalOnServerRole({ServerRole.WORKER, ServerRole.ALL})
public class ChatRoomCloseScheduler {

    private final ChatRoomCloseService chatRoomCloseService;

    @Scheduled(fixedDelayString = "${moyeota.chat.close-sweep-delay}")
    public void sweep() {
        chatRoomCloseService.closeAfterRide();
    }
}
