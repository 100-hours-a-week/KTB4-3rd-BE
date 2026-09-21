package com.ktb.moyeota.domain.companionpost.controller;

import com.ktb.moyeota.domain.companionpost.dto.CompanionPostCreateRequest;
import com.ktb.moyeota.domain.companionpost.dto.CompanionPostCreateResponse;
import com.ktb.moyeota.domain.companionpost.dto.CompanionPostDetailResponse;
import com.ktb.moyeota.domain.companionpost.service.CompanionPostService;
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
@RequestMapping("/companion-posts")
@RequiredArgsConstructor
public class CompanionPostController {

    private final CompanionPostService companionPostService;

    @PostMapping
    public ResponseEntity<ApiResponse<CompanionPostCreateResponse>> create(
            @AuthUser Long userId,
            @Valid @RequestBody CompanionPostCreateRequest request
    ) {
        CompanionPostCreateResponse response = companionPostService.create(userId, request);
        return ResponseEntity
                .created(URI.create("/companion-posts/" + response.id()))
                .body(ApiResponse.success("게시글이 등록되었습니다", response));
    }

    @GetMapping("/{companion_id}")
    public ResponseEntity<ApiResponse<CompanionPostDetailResponse>> get(
            @AuthUser Long userId,
            @PathVariable("companion_id") Long companionId
    ) {
        CompanionPostDetailResponse response = companionPostService.find(userId, companionId);
        return ResponseEntity.ok(ApiResponse.success("조회에 성공하였습니다.", response));
    }
}
