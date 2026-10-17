package com.ktb.moyeota.domain.chat.controller;

import com.ktb.moyeota.domain.chat.dto.ChatMessageSendRequest;
import com.ktb.moyeota.global.websocket.send.ChatMessageSender;
import jakarta.validation.Valid;
import java.security.Principal;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Controller;

@Controller
@MessageMapping("/chat")
@RequiredArgsConstructor
public class ChatStompController {

    private final ChatMessageSender chatMessageSender;

    @MessageMapping("/{room_id}")
    public void send(@DestinationVariable("room_id") Long roomId,
                     @Valid @Payload ChatMessageSendRequest request,
                     Principal principal) {
        chatMessageSender.send(roomId, Long.valueOf(principal.getName()), request);
    }
}
