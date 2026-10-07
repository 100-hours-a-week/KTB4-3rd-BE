package com.ktb.moyeota.global.external.kakao;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "moyeota.kakao-unlink")
public record KakaoUnlinkProperties(String unlinkUri, String adminKey) {

    public KakaoUnlinkProperties {
        if (unlinkUri == null || unlinkUri.isBlank()) {
            throw new IllegalStateException("moyeota.kakao-unlink.unlink-uri가 필요합니다.");
        }
    }

    public boolean hasAdminKey() {
        return adminKey != null && !adminKey.isBlank();
    }
}
