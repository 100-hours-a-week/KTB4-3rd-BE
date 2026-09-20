package com.ktb.moyeota.domain.auth.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.ktb.moyeota.domain.auth.model.AuthorizeRedirect;
import com.ktb.moyeota.domain.auth.model.OAuthProvider;
import com.ktb.moyeota.global.external.kakao.KakaoOAuthClient;
import com.ktb.moyeota.global.external.kakao.KakaoProperties;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.web.util.UriComponentsBuilder;

class OAuthLoginServiceTest {

    private final OAuthLoginService service = new OAuthLoginService(new KakaoOAuthClient(new KakaoProperties(
            "test-kakao-client-id",
            "http://localhost:8080/auth/kakao/callback",
            "https://kauth.kakao.com/oauth/authorize")));

    @Test
    @DisplayName("state는 32바이트 난수의 base64url 표현이다")
    void stateIsUrlSafe32Bytes() {
        AuthorizeRedirect redirect = service.buildAuthorizeRedirect(OAuthProvider.KAKAO);

        assertThat(redirect.state()).hasSize(43).matches("^[A-Za-z0-9_-]+$");
    }

    @Test
    @DisplayName("state는 호출마다 달라진다")
    void stateDiffersPerCall() {
        String first = service.buildAuthorizeRedirect(OAuthProvider.KAKAO).state();
        String second = service.buildAuthorizeRedirect(OAuthProvider.KAKAO).state();

        assertThat(first).isNotEqualTo(second);
    }

    @Test
    @DisplayName("쿠키에 담길 state와 인가 URI의 state가 같다")
    void locationCarriesSameState() {
        AuthorizeRedirect redirect = service.buildAuthorizeRedirect(OAuthProvider.KAKAO);

        String stateInLocation = UriComponentsBuilder.fromUri(redirect.location())
                .build().getQueryParams().getFirst("state");
        assertThat(stateInLocation).isEqualTo(redirect.state());
    }
}
