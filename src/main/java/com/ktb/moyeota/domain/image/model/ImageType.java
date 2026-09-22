package com.ktb.moyeota.domain.image.model;

import java.util.Arrays;
import java.util.Optional;

public enum ImageType {

    JPEG("image/jpeg", "jpg"),
    PNG("image/png", "png"),
    WEBP("image/webp", "webp");

    private final String contentType;
    private final String extension;

    ImageType(String contentType, String extension) {
        this.contentType = contentType;
        this.extension = extension;
    }

    public static Optional<ImageType> fromContentType(String contentType) {
        return Arrays.stream(values())
                .filter(type -> type.contentType.equals(contentType))
                .findFirst();
    }

    public String contentType() {
        return contentType;
    }

    public String extension() {
        return extension;
    }
}
