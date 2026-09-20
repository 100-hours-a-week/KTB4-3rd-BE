package com.ktb.moyeota.domain.community.controller;

import com.ktb.moyeota.domain.community.dto.CommunityCommentCreateRequest;
import com.ktb.moyeota.domain.community.dto.CommunityCommentCreateResponse;
import com.ktb.moyeota.domain.community.dto.CommunityCommentListResponse;
import com.ktb.moyeota.domain.community.service.CommunityCommentService;
import com.ktb.moyeota.global.common.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * POST /community-posts/{post_id}/comments, GET /community-posts/{post_id}/comments.
 *
 * [TODO 확인 필요] 테크스펙 원문의 "게시글 엔티티를 이용하여 게시글- 404" / "게시글 엔티티를
 * 이용하여 게시글 - 404" 문구가 어색해서(오타/누락으로 보임) 정확히 뭘 의도했는지 애매함 —
 * 지금 구현은 "post_id로 게시글이 존재하지 않으면 404"로 해석해서 Service에 넣어뒀다.
 * [TODO 확인 필요] 같은 "게시글 삭제됨" 상태인데 댓글 작성은 409, 댓글 목록조회는 410으로
 * 다르게 응답한다(테크스펙에 "댓글 작성 409와 상태 코드가 다른 점 주의"라고 명시는 돼 있음) —
 * 의도한 게 맞는지 한번 더 확인 필요.
 */
@RestController
@RequestMapping("/community-posts/{post_id}/comments")
@RequiredArgsConstructor
public class CommunityCommentController {

    private final CommunityCommentService communityCommentService;

    @PostMapping
    public ResponseEntity<ApiResponse<CommunityCommentCreateResponse>> create(
            @AuthenticationPrincipal Long userId,
            @PathVariable("post_id") Long postId,
            @Valid @RequestBody CommunityCommentCreateRequest request
    ) {
        CommunityCommentCreateResponse response = communityCommentService.create(userId, postId, request);
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(ApiResponse.success("댓글이 등록되었습니다", response));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<CommunityCommentListResponse>> list(
            @PathVariable("post_id") Long postId,
            @RequestParam(value = "cursor", required = false) Long cursor
    ) {
        CommunityCommentListResponse response = communityCommentService.findAll(postId, cursor);
        return ResponseEntity.ok(ApiResponse.success(null, response));
    }
}
