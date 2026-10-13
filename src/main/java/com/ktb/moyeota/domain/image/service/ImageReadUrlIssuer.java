package com.ktb.moyeota.domain.image.service;

import com.ktb.moyeota.global.external.s3.S3Properties;
import java.time.Duration;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;

@Component
@RequiredArgsConstructor
public class ImageReadUrlIssuer {

    private static final Duration READ_URL_TTL = Duration.ofMinutes(30);

    private final S3Presigner s3Presigner;
    private final S3Properties s3Properties;

    public String issue(String key) {
        return s3Presigner.presignGetObject(GetObjectPresignRequest.builder()
                        .signatureDuration(READ_URL_TTL)
                        .getObjectRequest(GetObjectRequest.builder()
                                .bucket(s3Properties.bucket())
                                .key(key)
                                .build())
                        .build())
                .url()
                .toString();
    }
}
