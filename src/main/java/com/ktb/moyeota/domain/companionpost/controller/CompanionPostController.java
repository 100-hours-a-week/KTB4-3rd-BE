package com.ktb.moyeota.domain.companionpost.controller;

import com.ktb.moyeota.domain.companionpost.dto.CompanionPostCreateRequest;
import com.ktb.moyeota.domain.companionpost.dto.CompanionPostCreateResponse;
import com.ktb.moyeota.domain.companionpost.dto.CompanionPostDetailResponse;
import com.ktb.moyeota.domain.companionpost.service.CompanionPostService;
import com.ktb.moyeota.global.common.ApiResponse;
import jakarta.validation.Valid;
import java.net.URI;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * POST /companion-posts, GET /companion-posts/{companion_id}.
 *
 * [주의할 내용] CLAUD.md 4장 기준으로는 이 API들은 COMMUNITY가 아니라 COMPANION(Companion
 * Recruitment) 도메인 소관이다. 다만 이번 세션에서 이미 domain/companionpost 패키지(Repository,
 * DTO)를 테크스펙 문서의 명시적 지시(92번째 줄: "실제 구현 시 패키지·클래스는 두 도메인으로
 * 분리한다")에 따라 만들어 둔 상태라, 그 연장선에서 Controller도 같은 패키지에 작성했다.
 * 별도 도메인으로 취급해야 한다면(예: Companion Recruitment를 완전히 분리된 작업 단위로
 * 진행 중이라면) 이 파일은 별개 작업으로 빼야 할 수 있음.
 *
 * [주의할 내용] userId에 쓴 @AuthenticationPrincipal도 CommunityPostController와 동일하게
 * 아직 Auth 인프라가 없어 실제로는 동작하지 않는다.
 */
@RestController
@RequestMapping("/companion-posts")
@RequiredArgsConstructor
public class CompanionPostController {

    private final CompanionPostService companionPostService;

    @PostMapping
    public ResponseEntity<ApiResponse<CompanionPostCreateResponse>> create(
            @AuthenticationPrincipal Long userId,
            @Valid @RequestBody CompanionPostCreateRequest request
    ) {
        CompanionPostCreateResponse response = companionPostService.create(userId, request);
        return ResponseEntity
                .created(URI.create("/companion-posts/" + response.id()))
                .body(ApiResponse.success("게시글이 등록되었습니다", response));
    }

    @GetMapping("/{companion_id}")
    public ResponseEntity<ApiResponse<CompanionPostDetailResponse>> get(
            @PathVariable("companion_id") Long companionId
    ) {
        CompanionPostDetailResponse response = companionPostService.find(companionId);
        return ResponseEntity.ok(ApiResponse.success(null, response));
    }
}
