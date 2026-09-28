package com.ktb.moyeota.domain.chat.controller;

import com.ktb.moyeota.domain.chat.dto.ChatMessageSendRequest;
import com.ktb.moyeota.domain.chat.service.ChatMessageSendService;
import java.security.Principal;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;


@Controller
@MessageMapping("/chat")
@RequiredArgsConstructor
public class ChatStompController {

    private static final String BROKER_DESTINATION_PREFIX = "/sub/chat/";

    private final ChatMessageSendService chatMessageSendService;
    private final SimpMessagingTemplate messagingTemplate;

    @MessageMapping("/{room_id}")
    public void send(@DestinationVariable("room_id") Long roomId,
                      @Payload ChatMessageSendRequest request,
                      Principal principal) {
        Long userId = Long.valueOf(principal.getName());

        chatMessageSendService.send(userId, roomId, request)
                .ifPresent(item -> messagingTemplate.convertAndSend(BROKER_DESTINATION_PREFIX + roomId, item));
    }
}
