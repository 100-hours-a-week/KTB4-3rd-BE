package com.ktb.moyeota.domain.chat.controller;

import com.ktb.moyeota.domain.chat.dto.ChatRoomDetailResponse;
import com.ktb.moyeota.domain.chat.dto.ChatRoomListResponse;
import com.ktb.moyeota.domain.chat.dto.ReadMarkerRequest;
import com.ktb.moyeota.domain.chat.dto.ReadMarkerResponse;
import com.ktb.moyeota.domain.chat.service.ChatRoomService;
import com.ktb.moyeota.domain.companion.entity.CompanionKind;
import com.ktb.moyeota.global.common.ApiResponse;
import com.ktb.moyeota.global.security.resolver.AuthUser;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/chat-rooms")
@RequiredArgsConstructor
public class ChatRoomController {

    private final ChatRoomService chatRoomService;

    @GetMapping
    public ResponseEntity<ApiResponse<ChatRoomListResponse>> list(
            @AuthUser Long userId,
            @RequestParam(value = "kind", required = false) CompanionKind kind,
            @RequestParam(value = "cursor", required = false) String cursor
    ) {
        ChatRoomListResponse response = chatRoomService.findMyChatRooms(userId, kind, cursor);
        return ResponseEntity.ok(ApiResponse.success("조회에 성공했습니다", response));
    }

    @GetMapping("/{room_id}")
    public ResponseEntity<ApiResponse<ChatRoomDetailResponse>> get(
            @AuthUser Long userId,
            @PathVariable("room_id") Long roomId
    ) {
        ChatRoomDetailResponse response = chatRoomService.findDetail(userId, roomId);
        return ResponseEntity.ok(ApiResponse.success("조회에 성공했습니다", response));
    }

    @PutMapping("/{room_id}/read-marker")
    public ResponseEntity<ApiResponse<ReadMarkerResponse>> markRead(
            @AuthUser Long userId,
            @PathVariable("room_id") Long roomId,
            @RequestBody ReadMarkerRequest request
    ) {
        ReadMarkerResponse response = chatRoomService.markRead(userId, roomId, request);
        return ResponseEntity.ok(ApiResponse.success("읽음 처리에 성공했습니다", response));
    }
}
