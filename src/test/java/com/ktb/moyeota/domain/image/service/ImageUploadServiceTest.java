package com.ktb.moyeota.domain.image.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.ktb.moyeota.domain.image.model.ImagePurpose;
import com.ktb.moyeota.domain.image.model.ImageType;
import com.ktb.moyeota.domain.image.model.IssuedUploadUrl;
import com.ktb.moyeota.domain.image.model.PresignedUrlCommand;
import com.ktb.moyeota.domain.image.model.UploadScope;
import com.ktb.moyeota.global.external.s3.S3Properties;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

class ImageUploadServiceTest {

    private static final S3Properties PROPERTIES =
            new S3Properties("moyeota-test-images", "ap-northeast-2", Duration.ofMinutes(5), null);
    private static final PresignedUrlCommand JPEG_482KB =
            new PresignedUrlCommand(ImagePurpose.PROFILE, ImageType.JPEG, 482_113L);

    private final S3Presigner presigner = S3Presigner.builder()
            .region(Region.of(PROPERTIES.region()))
            .credentialsProvider(StaticCredentialsProvider.create(
                    AwsBasicCredentials.create("test-access-key", "test-secret-key")))
            .build();

    private final ImageUploadService service = new ImageUploadService(presigner, PROPERTIES);

    @AfterEach
    void tearDown() {
        presigner.close();
    }

    @Test
    @DisplayName("키는 tmp/{용도}/{scope}/{uuid}.{확장자} 형식이고 서버가 정한다")
    void keyFollowsLayout() {
        IssuedUploadUrl issued = service.issue(UploadScope.signup("0123456789abcdef0123"), JPEG_482KB);

        assertThat(issued.imageKey())
                .matches("^tmp/profile/s-0123456789abcdef/[0-9a-f-]{36}\\.jpg$");
    }

    @Test
    @DisplayName("회원 scope면 키에 사용자 ID가 들어간다")
    void userScopeInKey() {
        IssuedUploadUrl issued = service.issue(UploadScope.user(42L),
                new PresignedUrlCommand(ImagePurpose.PROFILE, ImageType.WEBP, 100L));

        assertThat(issued.imageKey()).startsWith("tmp/profile/u-42/").endsWith(".webp");
    }

    @Test
    @DisplayName("URL은 버킷의 그 키를 가리키고 수명은 설정값이다")
    void urlPointsToBucketKey() {
        IssuedUploadUrl issued = service.issue(UploadScope.user(42L), JPEG_482KB);

        assertThat(issued.uploadUrl().getHost()).isEqualTo("moyeota-test-images.s3.ap-northeast-2.amazonaws.com");
        assertThat(issued.uploadUrl().getPath()).isEqualTo("/" + issued.imageKey());
        assertThat(issued.uploadUrl().getQuery()).contains("X-Amz-Expires=300");
        assertThat(issued.expiresIn()).isEqualTo(300);
    }

    @Test
    @DisplayName("타입과 크기가 서명에 묶여 다른 값으로는 올릴 수 없다")
    void contentTypeAndLengthAreSigned() {
        IssuedUploadUrl issued = service.issue(UploadScope.user(42L), JPEG_482KB);

        String signedHeaders = URLDecoder.decode(
                queryParam(issued.uploadUrl().getQuery(), "X-Amz-SignedHeaders"), StandardCharsets.UTF_8);

        assertThat(signedHeaders.split(";")).contains("content-type", "content-length", "host");
    }

    @Test
    @DisplayName("같은 요청이라도 발급마다 키가 다르다")
    void keysAreUnique() {
        IssuedUploadUrl first = service.issue(UploadScope.user(42L), JPEG_482KB);
        IssuedUploadUrl second = service.issue(UploadScope.user(42L), JPEG_482KB);

        assertThat(first.imageKey()).isNotEqualTo(second.imageKey());
    }

    private static String queryParam(String query, String name) {
        for (String pair : query.split("&")) {
            String[] kv = pair.split("=", 2);
            if (kv[0].equals(name)) {
                return kv[1];
            }
        }
        throw new AssertionError("no query param " + name);
    }
}
