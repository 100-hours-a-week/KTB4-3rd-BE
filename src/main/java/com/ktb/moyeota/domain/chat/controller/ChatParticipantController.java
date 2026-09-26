package com.ktb.moyeota.domain.chat.controller;

import com.ktb.moyeota.domain.chat.dto.ChatParticipateResponse;
import com.ktb.moyeota.domain.chat.service.ChatParticipationService;
import com.ktb.moyeota.global.common.ApiResponse;
import com.ktb.moyeota.global.security.resolver.AuthUser;
import java.net.URI;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/companion-posts/{companion_id}/participants")
@RequiredArgsConstructor
public class ChatParticipantController {

    private final ChatParticipationService chatParticipationService;

    @PostMapping
    public ResponseEntity<ApiResponse<ChatParticipateResponse>> participate(
            @AuthUser Long userId,
            @PathVariable("companion_id") Long companionId
    ) {
        ChatParticipateResponse response = chatParticipationService.participate(userId, companionId);
        return ResponseEntity
                .created(URI.create("/api/companion-posts/" + companionId
                        + "/participants/" + response.companionParticipantId()))
                .body(ApiResponse.success("채팅방에 참여했습니다", response));
    }


    @DeleteMapping("/me")
    public ResponseEntity<Void> leave(
            @AuthUser Long userId,
            @PathVariable("companion_id") Long companionId
    ) {
        chatParticipationService.leave(userId, companionId);
        return ResponseEntity.noContent().build();
    }
}
