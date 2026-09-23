package com.ktb.moyeota.domain.image.service;

import com.ktb.moyeota.domain.image.model.IssuedUploadUrl;
import com.ktb.moyeota.domain.image.model.PresignedUrlCommand;
import com.ktb.moyeota.domain.image.model.UploadScope;
import com.ktb.moyeota.global.exception.BusinessException;
import com.ktb.moyeota.global.exception.CommonErrorCode;
import com.ktb.moyeota.global.external.s3.S3Properties;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.PresignedPutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;

@Service
@RequiredArgsConstructor
public class ImageUploadService {

    private final S3Presigner s3Presigner;
    private final S3Properties s3Properties;

    public IssuedUploadUrl issue(UploadScope scope, PresignedUrlCommand command) {
        if (!scope.allows(command.purpose())) {
            throw new BusinessException(CommonErrorCode.FORBIDDEN);
        }
        String key = command.purpose().tmpPrefix() + scope.value() + "/"
                + UUID.randomUUID() + "." + command.type().extension();

        PresignedPutObjectRequest presigned = s3Presigner.presignPutObject(PutObjectPresignRequest.builder()
                .signatureDuration(s3Properties.presignTtl())
                .putObjectRequest(PutObjectRequest.builder()
                        .bucket(s3Properties.bucket())
                        .key(key)
                        .contentType(command.type().contentType())
                        .contentLength(command.contentLength())
                        .build())
                .build());

        return new IssuedUploadUrl(presigned.url(), key, s3Properties.presignTtl().toSeconds());
    }
}
