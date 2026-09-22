package com.ktb.moyeota.domain.image.model;

import java.util.Arrays;
import java.util.Optional;
import java.util.Set;

public enum ImagePurpose {

    PROFILE("tmp/profile/", "profile/", Set.of(ImageType.JPEG, ImageType.PNG, ImageType.WEBP), 5L * 1024 * 1024);

    private final String tmpPrefix;
    private final String finalPrefix;
    private final Set<ImageType> allowedTypes;
    private final long maxBytes;

    ImagePurpose(String tmpPrefix, String finalPrefix, Set<ImageType> allowedTypes, long maxBytes) {
        this.tmpPrefix = tmpPrefix;
        this.finalPrefix = finalPrefix;
        this.allowedTypes = allowedTypes;
        this.maxBytes = maxBytes;
    }

    public static Optional<ImagePurpose> fromName(String name) {
        return Arrays.stream(values())
                .filter(purpose -> purpose.name().equals(name))
                .findFirst();
    }

    public boolean allows(ImageType type) {
        return allowedTypes.contains(type);
    }

    public boolean fits(long contentLength) {
        return contentLength <= maxBytes;
    }

    public String tmpPrefix() {
        return tmpPrefix;
    }

    public String finalPrefix() {
        return finalPrefix;
    }

    public long maxBytes() {
        return maxBytes;
    }
}
