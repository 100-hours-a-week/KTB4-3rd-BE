package com.ktb.moyeota.domain.image.controller;

import com.ktb.moyeota.domain.image.dto.PresignedUrlRequest;
import com.ktb.moyeota.domain.image.dto.PresignedUrlResponse;
import com.ktb.moyeota.domain.image.model.IssuedUploadUrl;
import com.ktb.moyeota.domain.image.model.UploadScope;
import com.ktb.moyeota.domain.image.service.ImageUploadService;
import com.ktb.moyeota.global.common.ApiResponse;
import com.ktb.moyeota.global.security.resolver.UploadPrincipal;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class ImageUploadController {

    private static final String ISSUED = "이미지 업로드 URL이 발급되었습니다.";

    private final ImageUploadService imageUploadService;

    @PostMapping("/images/presigned-url")
    public ApiResponse<PresignedUrlResponse> issuePresignedUrl(
            @UploadPrincipal UploadScope scope,
            @Valid @RequestBody PresignedUrlRequest request) {

        IssuedUploadUrl issued = imageUploadService.issue(scope, request.toCommand());
        return ApiResponse.success(ISSUED, PresignedUrlResponse.from(issued));
    }
}
