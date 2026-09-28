package com.ktb.moyeota.domain.chat.controller;

import com.ktb.moyeota.domain.chat.dto.MessageListResponse;
import com.ktb.moyeota.domain.chat.service.ChatMessageService;
import com.ktb.moyeota.global.common.ApiResponse;
import com.ktb.moyeota.global.security.resolver.AuthUser;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/chat-rooms/{room_id}/messages")
@RequiredArgsConstructor
public class ChatMessageController {

    private final ChatMessageService chatMessageService;

    @GetMapping
    public ResponseEntity<ApiResponse<MessageListResponse>> list(
            @AuthUser Long userId,
            @PathVariable("room_id") Long roomId,
            @RequestParam(value = "cursor", required = false) String cursor
    ) {
        MessageListResponse response = chatMessageService.findMessages(userId, roomId, cursor);
        return ResponseEntity.ok(ApiResponse.success("조회에 성공했습니다", response));
    }
}
