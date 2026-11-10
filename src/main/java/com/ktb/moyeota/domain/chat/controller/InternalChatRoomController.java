package com.ktb.moyeota.domain.chat.controller;

import com.ktb.moyeota.domain.chat.dto.InternalChatMessageSendRequest;
import com.ktb.moyeota.domain.chat.dto.MessageItem;
import com.ktb.moyeota.domain.chat.dto.ParticipantCheckResponse;
import com.ktb.moyeota.domain.chat.exception.ChatErrorCode;
import com.ktb.moyeota.domain.chat.service.ChatMessageSendService;
import com.ktb.moyeota.domain.chat.service.ChatRoomService;
import com.ktb.moyeota.domain.chat.success.ChatSuccessCode;
import com.ktb.moyeota.global.common.ApiResponse;
import com.ktb.moyeota.global.exception.BusinessException;
import com.ktb.moyeota.global.websocket.relay.WsRelayPublisher;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/internal/chat-rooms/{room_id}")
@RequiredArgsConstructor
public class InternalChatRoomController {

    private static final String ROOM_DESTINATION_PREFIX = "/sub/chat/";

    private final ChatRoomService chatRoomService;
    private final ChatMessageSendService chatMessageSendService;
    private final WsRelayPublisher wsRelayPublisher;

    @GetMapping("/participants/{user_id}")
    public ResponseEntity<ApiResponse<ParticipantCheckResponse>> checkParticipant(
            @PathVariable("room_id") Long roomId,
            @PathVariable("user_id") Long userId
    ) {
        boolean isParticipant = chatRoomService.isActiveParticipant(roomId, userId);
        return ResponseEntity.ok(ApiResponse.of(ChatSuccessCode.PARTICIPANT_CHECKED, new ParticipantCheckResponse(isParticipant)));
    }

    @PostMapping("/messages")
    public ResponseEntity<ApiResponse<MessageItem>> sendMessage(
            @PathVariable("room_id") Long roomId,
            @Valid @RequestBody InternalChatMessageSendRequest request
    ) {
        MessageItem item = chatMessageSendService.send(request.userId(), roomId, request.toSendRequest())
                .orElseThrow(() -> new BusinessException(ChatErrorCode.CHATROOM_NOT_FOUND));
        wsRelayPublisher.toRoom(roomId, ROOM_DESTINATION_PREFIX + roomId, item);
        return ResponseEntity.ok(ApiResponse.of(ChatSuccessCode.MESSAGE_SENT, item));
    }
}
