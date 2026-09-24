package com.ktb.moyeota.domain.community.controller;

import com.ktb.moyeota.domain.community.dto.CommunityCommentCreateRequest;
import com.ktb.moyeota.domain.community.dto.CommunityCommentCreateResponse;
import com.ktb.moyeota.domain.community.dto.CommunityCommentListResponse;
import com.ktb.moyeota.domain.community.service.CommunityCommentService;
import com.ktb.moyeota.global.common.ApiResponse;
import com.ktb.moyeota.global.security.resolver.AuthUser;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/community-posts/{post_id}/comments")
@RequiredArgsConstructor
public class CommunityCommentController {

    private final CommunityCommentService communityCommentService;

    @PostMapping
    public ResponseEntity<ApiResponse<CommunityCommentCreateResponse>> create(
            @AuthUser Long userId,
            @PathVariable("post_id") Long postId,
            @Valid @RequestBody CommunityCommentCreateRequest request
    ) {
        CommunityCommentCreateResponse response = communityCommentService.create(userId, postId, request);
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(ApiResponse.success("댓글이 등록되었습니다", response));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<CommunityCommentListResponse>> get(
            @PathVariable("post_id") Long postId,
            @RequestParam(value = "cursor", required = false) Long cursor
    ) {
        CommunityCommentListResponse response = communityCommentService.findAll(postId, cursor);
        return ResponseEntity.ok(ApiResponse.success(null, response));
    }
}
