package com.ktb.moyeota.domain.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.ktb.moyeota.domain.auth.model.AuthorizeRedirect;
import com.ktb.moyeota.domain.auth.model.OAuthProvider;
import com.ktb.moyeota.global.external.kakao.KakaoOAuthClient;
import java.net.URI;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class OAuthLoginServiceTest {

    private final KakaoOAuthClient kakaoOAuthClient = mock(KakaoOAuthClient.class);
    private final OpaqueTokenFactory opaqueTokenFactory = new OpaqueTokenFactory();

    private final OAuthLoginService service = new OAuthLoginService(kakaoOAuthClient, opaqueTokenFactory);

    @Nested
    @DisplayName("로그인 진입")
    class BuildAuthorizeRedirect {

        @Test
        @DisplayName("state는 32바이트 난수의 base64url 표현이고 호출마다 달라진다")
        void stateIsRandomAndUrlSafe() {
            given(kakaoOAuthClient.buildAuthorizeUri(any())).willReturn(URI.create("https://kauth"));

            String first = service.buildAuthorizeRedirect(OAuthProvider.KAKAO).state();
            String second = service.buildAuthorizeRedirect(OAuthProvider.KAKAO).state();

            assertThat(first).hasSize(43).matches("^[A-Za-z0-9_-]+$");
            assertThat(first).isNotEqualTo(second);
        }

        @Test
        @DisplayName("쿠키에 담길 state를 그대로 인가 URI 조립에 넘긴다")
        void passesSameStateToClient() {
            given(kakaoOAuthClient.buildAuthorizeUri(any())).willReturn(URI.create("https://kauth"));

            AuthorizeRedirect redirect = service.buildAuthorizeRedirect(OAuthProvider.KAKAO);

            verify(kakaoOAuthClient).buildAuthorizeUri(redirect.state());
            assertThat(redirect.location()).isEqualTo(URI.create("https://kauth"));
        }
    }
}
