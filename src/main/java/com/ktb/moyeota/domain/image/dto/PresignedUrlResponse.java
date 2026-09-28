package com.ktb.moyeota.domain.image.dto;

import com.ktb.moyeota.domain.image.model.IssuedUploadUrl;

public record PresignedUrlResponse(String uploadUrl, String imageKey, long expiresIn) {

    public static PresignedUrlResponse from(IssuedUploadUrl issued) {
        return new PresignedUrlResponse(issued.uploadUrl().toString(), issued.imageKey(), issued.expiresIn());
    }
}
