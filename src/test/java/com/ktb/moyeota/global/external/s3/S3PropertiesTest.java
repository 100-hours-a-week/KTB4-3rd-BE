package com.ktb.moyeota.global.external.s3;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class S3PropertiesTest {

    private static final Duration TTL = Duration.ofMinutes(5);

    @Test
    @DisplayName("모든 값이 있으면 통과한다")
    void acceptsCompleteValues() {
        assertThatCode(() -> new S3Properties("moyeota-images", "ap-northeast-2", TTL, null))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("버킷 이름이 없으면 기동 시점에 거부한다")
    void rejectsMissingBucket() {
        assertThatThrownBy(() -> new S3Properties(" ", "ap-northeast-2", TTL, null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("bucket");
    }

    @Test
    @DisplayName("리전이 없으면 거부한다")
    void rejectsMissingRegion() {
        assertThatThrownBy(() -> new S3Properties("moyeota-images", null, TTL, null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("region");
    }

    @Test
    @DisplayName("URL 수명이 없거나 0 이하면 거부한다")
    void rejectsNonPositiveTtl() {
        assertThatThrownBy(() -> new S3Properties("moyeota-images", "ap-northeast-2", null, null))
                .hasMessageContaining("presign-ttl");
        assertThatThrownBy(() -> new S3Properties("moyeota-images", "ap-northeast-2", Duration.ZERO, null))
                .hasMessageContaining("presign-ttl");
    }

    @Test
    @DisplayName("공개 주소가 없으면 버킷의 S3 주소를 쓴다")
    void defaultsPublicBaseUrlToBucketEndpoint() {
        assertThat(new S3Properties("moyeota-images", "ap-northeast-2", TTL, null).publicBaseUrl())
                .isEqualTo("https://moyeota-images.s3.ap-northeast-2.amazonaws.com");
        assertThat(new S3Properties("moyeota-images", "ap-northeast-2", TTL, " ").publicBaseUrl())
                .isEqualTo("https://moyeota-images.s3.ap-northeast-2.amazonaws.com");
    }

    @Test
    @DisplayName("공개 주소 끝의 슬래시는 떼어 낸다")
    void stripsTrailingSlash() {
        S3Properties properties = new S3Properties("moyeota-images", "ap-northeast-2", TTL, "https://cdn.moyeota.app/");

        assertThat(properties.publicBaseUrl())
                .isEqualTo("https://cdn.moyeota.app");
    }
}
