package com.ktb.moyeota.domain.auth.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.ktb.moyeota.domain.auth.model.OAuthFront;
import com.ktb.moyeota.domain.auth.model.OAuthProvider;
import com.ktb.moyeota.global.config.OAuthProperties;
import com.ktb.moyeota.global.external.kakao.KakaoProperties;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

class OAuthFrontResolverTest {

    private static final String DEFAULT_CALLBACK = "http://dev.moyeota.com/auth/callback";
    private static final String DEFAULT_REDIRECT = "http://dev.moyeota.com/api/auth/kakao/callback";
    private static final String LOCAL_ORIGIN = "http://localhost:3000";

    private final OAuthFrontResolver resolver = new OAuthFrontResolver(
            new OAuthProperties(DEFAULT_CALLBACK, List.of(LOCAL_ORIGIN)),
            new KakaoProperties("id", "secret", DEFAULT_REDIRECT,
                    "https://kauth.kakao.com/oauth/authorize", "https://kauth.kakao.com/oauth/token",
                    "https://kapi.kakao.com/v2/user/me"));

    @Test
    @DisplayName("허용된 오리진이면 기본 설정의 경로를 그 오리진에 붙인다")
    void allowedOriginReusesDefaultPaths() {
        assertThat(resolver.resolve(OAuthProvider.KAKAO, LOCAL_ORIGIN)).isEqualTo(new OAuthFront(
                LOCAL_ORIGIN,
                LOCAL_ORIGIN + "/api/auth/kakao/callback",
                LOCAL_ORIGIN + "/auth/callback"));
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", "http://evil.example", "http://localhost:3000/", "https://localhost:3000"})
    @DisplayName("오리진이 없거나 허용 목록과 정확히 같지 않으면 기본 프론트다")
    void otherwiseDefault(String origin) {
        OAuthFront front = resolver.resolve(OAuthProvider.KAKAO, origin);

        assertThat(front).isEqualTo(new OAuthFront(null, DEFAULT_REDIRECT, DEFAULT_CALLBACK));
        assertThat(front.isDefault()).isTrue();
    }
}
