package com.ktb.moyeota.domain.image.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.ktb.moyeota.domain.image.error.ImageErrorCode;
import com.ktb.moyeota.domain.image.model.ImagePurpose;
import com.ktb.moyeota.domain.image.model.UploadScope;
import com.ktb.moyeota.global.exception.BusinessException;
import com.ktb.moyeota.global.exception.CommonErrorCode;
import com.ktb.moyeota.global.external.s3.S3Properties;
import java.time.Duration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.CopyObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;

class ImagePromotionServiceTest {

    private static final String BUCKET = "moyeota-test-images";
    private static final UploadScope SCOPE = UploadScope.signup("4e30b9ed47b5ea2e" + "0".repeat(48));
    private static final String FILE_NAME = "2aac932d-f68b-4491-9891-fd47671fcf02.jpg";
    private static final String TMP_KEY = "tmp/profile/s-4e30b9ed47b5ea2e/" + FILE_NAME;

    private final S3Client s3Client = mock(S3Client.class);
    private final ImagePromotionService service = new ImagePromotionService(
            s3Client, new S3Properties(BUCKET, "ap-northeast-2", Duration.ofMinutes(5), null));

    @Test
    @DisplayName("업로드된 임시 이미지를 확정 경로로 복사하고 확정 키를 돌려준다")
    void copiesToFinalPrefix() {
        String finalKey = service.promote(SCOPE, ImagePurpose.PROFILE, TMP_KEY);

        assertThat(finalKey).isEqualTo("profile/" + FILE_NAME);
        ArgumentCaptor<HeadObjectRequest> head = ArgumentCaptor.forClass(HeadObjectRequest.class);
        verify(s3Client).headObject(head.capture());
        assertThat(head.getValue().bucket()).isEqualTo(BUCKET);
        assertThat(head.getValue().key()).isEqualTo(TMP_KEY);
        ArgumentCaptor<CopyObjectRequest> copy = ArgumentCaptor.forClass(CopyObjectRequest.class);
        verify(s3Client).copyObject(copy.capture());
        assertThat(copy.getValue().sourceBucket()).isEqualTo(BUCKET);
        assertThat(copy.getValue().sourceKey()).isEqualTo(TMP_KEY);
        assertThat(copy.getValue().destinationBucket()).isEqualTo(BUCKET);
        assertThat(copy.getValue().destinationKey()).isEqualTo("profile/" + FILE_NAME);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "tmp/profile/s-ffffffffffffffff/2aac932d-f68b-4491-9891-fd47671fcf02.jpg",
            "tmp/profile/u-42/2aac932d-f68b-4491-9891-fd47671fcf02.jpg",
            "profile/2aac932d-f68b-4491-9891-fd47671fcf02.jpg",
            "tmp/chat/s-4e30b9ed47b5ea2e/2aac932d-f68b-4491-9891-fd47671fcf02.jpg",
            "tmp/profile/s-4e30b9ed47b5ea2e/",
            "tmp/profile/s-4e30b9ed47b5ea2e/nested/2aac932d-f68b-4491-9891-fd47671fcf02.jpg",
            "tmp/profile/s-4e30b9ed47b5ea2e/2aac932d-f68b-4491-9891-fd47671fcf02.gif",
            "tmp/profile/s-4e30b9ed47b5ea2e/not-a-uuid.jpg",
            ""
    })
    @DisplayName("이 범위에서 발급한 형식이 아닌 키는 S3를 부르기 전에 VALIDATION_ERROR로 거부한다")
    void rejectsKeysOutsideScope(String key) {
        assertThatThrownBy(() -> service.promote(SCOPE, ImagePurpose.PROFILE, key))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(CommonErrorCode.VALIDATION_ERROR));

        verifyNoInteractions(s3Client);
    }

    @Test
    @DisplayName("임시 이미지가 없으면 IMAGE_NOT_EXISTS이고 복사하지 않는다")
    void missingUploadIsImageNotExists() {
        given(s3Client.headObject(any(HeadObjectRequest.class)))
                .willThrow(S3Exception.builder().statusCode(404).build());

        assertThatThrownBy(() -> service.promote(SCOPE, ImagePurpose.PROFILE, TMP_KEY))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ImageErrorCode.IMAGE_NOT_EXISTS));

        verify(s3Client, never()).copyObject(any(CopyObjectRequest.class));
    }

    @Test
    @DisplayName("404가 아닌 S3 오류는 그대로 올려 보낸다")
    void otherHeadFailuresPropagate() {
        S3Exception forbidden = (S3Exception) S3Exception.builder().statusCode(403).build();
        given(s3Client.headObject(any(HeadObjectRequest.class))).willThrow(forbidden);

        assertThatThrownBy(() -> service.promote(SCOPE, ImagePurpose.PROFILE, TMP_KEY)).isSameAs(forbidden);

        verify(s3Client, never()).copyObject(any(CopyObjectRequest.class));
    }

    @Test
    @DisplayName("복사가 실패하면 그 오류를 그대로 올려 보낸다")
    void copyFailurePropagates() {
        S3Exception serverError = (S3Exception) S3Exception.builder().statusCode(500).build();
        given(s3Client.copyObject(any(CopyObjectRequest.class))).willThrow(serverError);

        assertThatThrownBy(() -> service.promote(SCOPE, ImagePurpose.PROFILE, TMP_KEY)).isSameAs(serverError);
    }
}
