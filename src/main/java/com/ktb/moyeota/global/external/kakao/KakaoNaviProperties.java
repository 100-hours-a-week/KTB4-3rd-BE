package com.ktb.moyeota.global.external.kakao;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "moyeota.kakao-navi")
public record KakaoNaviProperties(String directionsUri) {

    public KakaoNaviProperties {
        if (directionsUri == null || directionsUri.isBlank()) {
            throw new IllegalStateException("moyeota.kakao-navi.directions-uri가 필요합니다.");
        }
    }
}
