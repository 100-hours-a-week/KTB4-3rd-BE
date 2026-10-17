package com.ktb.moyeota.domain.chat.controller;

import com.ktb.moyeota.domain.chat.dto.ParticipantCheckResponse;
import com.ktb.moyeota.domain.chat.service.ChatRoomService;
import com.ktb.moyeota.global.common.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/internal/chat-rooms/{room_id}")
@RequiredArgsConstructor
public class InternalChatRoomController {

    private final ChatRoomService chatRoomService;

    @GetMapping("/participants/{user_id}")
    public ResponseEntity<ApiResponse<ParticipantCheckResponse>> checkParticipant(
            @PathVariable("room_id") Long roomId,
            @PathVariable("user_id") Long userId
    ) {
        boolean isParticipant = chatRoomService.isActiveParticipant(roomId, userId);
        return ResponseEntity.ok(ApiResponse.success("참여 여부를 확인했습니다", new ParticipantCheckResponse(isParticipant)));
    }
}
