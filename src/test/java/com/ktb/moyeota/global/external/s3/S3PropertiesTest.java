package com.ktb.moyeota.global.external.s3;

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
        assertThatCode(() -> new S3Properties("moyeota-images", "ap-northeast-2", TTL))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("버킷 이름이 없으면 기동 시점에 거부한다")
    void rejectsMissingBucket() {
        assertThatThrownBy(() -> new S3Properties(" ", "ap-northeast-2", TTL))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("bucket");
    }

    @Test
    @DisplayName("리전이 없으면 거부한다")
    void rejectsMissingRegion() {
        assertThatThrownBy(() -> new S3Properties("moyeota-images", null, TTL))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("region");
    }

    @Test
    @DisplayName("URL 수명이 없거나 0 이하면 거부한다")
    void rejectsNonPositiveTtl() {
        assertThatThrownBy(() -> new S3Properties("moyeota-images", "ap-northeast-2", null))
                .hasMessageContaining("presign-ttl");
        assertThatThrownBy(() -> new S3Properties("moyeota-images", "ap-northeast-2", Duration.ZERO))
                .hasMessageContaining("presign-ttl");
    }
}
