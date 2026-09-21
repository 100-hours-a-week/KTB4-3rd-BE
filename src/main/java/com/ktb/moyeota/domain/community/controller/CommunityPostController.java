package com.ktb.moyeota.domain.community.controller;

import com.ktb.moyeota.domain.community.dto.CommunityPostCreateRequest;
import com.ktb.moyeota.domain.community.dto.CommunityPostCreateResponse;
import com.ktb.moyeota.domain.community.dto.CommunityPostDetailResponse;
import com.ktb.moyeota.domain.community.service.CommunityPostService;
import com.ktb.moyeota.global.common.ApiResponse;
import com.ktb.moyeota.global.security.resolver.AuthUser;
import jakarta.validation.Valid;
import java.net.URI;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/community-posts")
@RequiredArgsConstructor
public class CommunityPostController {

    private final CommunityPostService communityPostService;

    @PostMapping
    public ResponseEntity<ApiResponse<CommunityPostCreateResponse>> create(
            @AuthUser Long userId,
            @Valid @RequestBody CommunityPostCreateRequest request
    ) {
        CommunityPostCreateResponse response = communityPostService.create(userId, request);
        return ResponseEntity
                .created(URI.create("/community-posts/" + response.id()))
                .body(ApiResponse.success("게시글이 등록되었습니다", response));
    }

    @GetMapping("/{post_id}")
    public ResponseEntity<ApiResponse<CommunityPostDetailResponse>> get(
            @PathVariable("post_id") Long postId
    ) {
        CommunityPostDetailResponse response = communityPostService.find(postId);
        return ResponseEntity.ok(ApiResponse.success("조회에 성공했습니다", response));
    }
}
