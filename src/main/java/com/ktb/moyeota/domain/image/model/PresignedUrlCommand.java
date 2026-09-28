package com.ktb.moyeota.domain.image.model;

public record PresignedUrlCommand(ImagePurpose purpose, ImageType type, long contentLength) {
}
