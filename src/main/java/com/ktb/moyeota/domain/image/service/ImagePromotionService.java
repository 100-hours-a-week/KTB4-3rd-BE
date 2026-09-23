package com.ktb.moyeota.domain.image.service;

import com.ktb.moyeota.domain.image.error.ImageErrorCode;
import com.ktb.moyeota.domain.image.model.ImagePurpose;
import com.ktb.moyeota.domain.image.model.ImageType;
import com.ktb.moyeota.domain.image.model.UploadScope;
import com.ktb.moyeota.global.exception.BusinessException;
import com.ktb.moyeota.global.exception.CommonErrorCode;
import com.ktb.moyeota.global.external.s3.S3Properties;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.CopyObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;

@Service
@RequiredArgsConstructor
public class ImagePromotionService {

    private static final Pattern FILE_NAME =
            Pattern.compile("^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}\\.([a-z]+)$");

    private final S3Client s3Client;
    private final S3Properties s3Properties;

    public String promote(UploadScope scope, ImagePurpose purpose, String tmpKey) {
        String fileName = ownedFileName(scope, purpose, tmpKey);
        requireUploaded(tmpKey);
        String finalKey = purpose.finalPrefix() + fileName;
        s3Client.copyObject(CopyObjectRequest.builder()
                .sourceBucket(s3Properties.bucket())
                .sourceKey(tmpKey)
                .destinationBucket(s3Properties.bucket())
                .destinationKey(finalKey)
                .build());
        return finalKey;
    }

    private String ownedFileName(UploadScope scope, ImagePurpose purpose, String tmpKey) {
        String prefix = purpose.tmpPrefix() + scope.value() + "/";
        if (!tmpKey.startsWith(prefix)) {
            throw new BusinessException(CommonErrorCode.VALIDATION_ERROR);
        }
        String fileName = tmpKey.substring(prefix.length());
        Matcher matcher = FILE_NAME.matcher(fileName);
        boolean allowedType = matcher.matches()
                && ImageType.fromExtension(matcher.group(1)).filter(purpose::allows).isPresent();
        if (!allowedType) {
            throw new BusinessException(CommonErrorCode.VALIDATION_ERROR);
        }
        return fileName;
    }

    private void requireUploaded(String key) {
        try {
            s3Client.headObject(HeadObjectRequest.builder()
                    .bucket(s3Properties.bucket())
                    .key(key)
                    .build());
        } catch (S3Exception e) {
            if (e.statusCode() == HttpStatus.NOT_FOUND.value()) {
                throw new BusinessException(ImageErrorCode.IMAGE_NOT_EXISTS);
            }
            throw e;
        }
    }
}
