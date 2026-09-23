package com.ktb.moyeota.global.external.s3;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "moyeota.storage.s3")
public record S3Properties(String bucket, String region, Duration presignTtl, String publicBaseUrl) {

    public S3Properties {
        requireText(bucket, "bucket");
        requireText(region, "region");
        if (presignTtl == null || presignTtl.isZero() || presignTtl.isNegative()) {
            throw new IllegalStateException("moyeota.storage.s3.presign-ttl은 0보다 커야 합니다.");
        }
        if (publicBaseUrl == null || publicBaseUrl.isBlank()) {
            publicBaseUrl = "https://%s.s3.%s.amazonaws.com".formatted(bucket, region);
        }
        publicBaseUrl = publicBaseUrl.replaceAll("/+$", "");
    }

    private static void requireText(String value, String key) {
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("moyeota.storage.s3.%s가 필요합니다.".formatted(key));
        }
    }
}
