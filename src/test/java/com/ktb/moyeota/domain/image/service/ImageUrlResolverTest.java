package com.ktb.moyeota.domain.image.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.ktb.moyeota.global.external.s3.S3Properties;
import java.time.Duration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ImageUrlResolverTest {

    private final ImageUrlResolver resolver = new ImageUrlResolver(new S3Properties(
            "moyeota-test-images", "ap-northeast-2", Duration.ofMinutes(5), "https://cdn.moyeota.app/"));

    @Test
    @DisplayName("키 앞에 공개 주소를 붙인다")
    void prefixesPublicBaseUrl() {
        assertThat(resolver.toUrl("profile/a.jpg")).isEqualTo("https://cdn.moyeota.app/profile/a.jpg");
    }

    @Test
    @DisplayName("키가 없으면 URL도 없다")
    void nullKeyIsNullUrl() {
        assertThat(resolver.toUrl(null)).isNull();
    }
}
