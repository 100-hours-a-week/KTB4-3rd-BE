package com.ktb.moyeota.domain.image.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.ktb.moyeota.global.external.s3.S3Properties;
import java.time.Duration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

class ImageReadUrlIssuerTest {

    private static final S3Properties PROPERTIES =
            new S3Properties("moyeota-test-images", "ap-northeast-2", Duration.ofMinutes(5), null);

    private final S3Presigner presigner = S3Presigner.builder()
            .region(Region.of(PROPERTIES.region()))
            .credentialsProvider(StaticCredentialsProvider.create(
                    AwsBasicCredentials.create("test-access-key", "test-secret-key")))
            .build();

    private final ImageReadUrlIssuer issuer = new ImageReadUrlIssuer(presigner, PROPERTIES);

    @AfterEach
    void tearDown() {
        presigner.close();
    }

    @Test
    @DisplayName("버킷의 그 키를 가리키는 서명된 GET 주소를 만든다")
    void signsKeyInBucket() {
        String url = issuer.issue("transfer/0b9c2f7e.jpg");

        assertThat(url)
                .startsWith("https://moyeota-test-images.s3.ap-northeast-2.amazonaws.com/transfer/0b9c2f7e.jpg?")
                .contains("X-Amz-Signature=");
    }

    @Test
    @DisplayName("읽기 주소는 30분 뒤 만료된다")
    void expiresAfterThirtyMinutes() {
        assertThat(issuer.issue("transfer/0b9c2f7e.jpg")).contains("X-Amz-Expires=1800");
    }
}
