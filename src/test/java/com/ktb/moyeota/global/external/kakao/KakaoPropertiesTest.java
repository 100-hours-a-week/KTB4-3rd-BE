package com.ktb.moyeota.global.external.kakao;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class KakaoPropertiesTest {

    private static final String CLIENT_ID = "test-kakao-client-id";
    private static final String CLIENT_SECRET = "test-kakao-client-secret";
    private static final String REDIRECT_URI = "http://localhost:8080/api/auth/kakao/callback";
    private static final String AUTHORIZE_URI = "https://kauth.kakao.com/oauth/authorize";
    private static final String TOKEN_URI = "https://kauth.kakao.com/oauth/token";
    private static final String USER_INFO_URI = "https://kapi.kakao.com/v2/user/me";

    @Test
    @DisplayName("모든 값이 있으면 통과한다")
    void acceptsCompleteValues() {
        assertThatCode(() -> new KakaoProperties(
                CLIENT_ID, CLIENT_SECRET, REDIRECT_URI, AUTHORIZE_URI, TOKEN_URI, USER_INFO_URI))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("REST API 키가 없으면 기동 시점에 거부한다")
    void rejectsMissingClientId() {
        assertThatThrownBy(() -> new KakaoProperties(
                null, CLIENT_SECRET, REDIRECT_URI, AUTHORIZE_URI, TOKEN_URI, USER_INFO_URI))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("client-id");
    }

    @Test
    @DisplayName("클라이언트 시크릿이 없으면 거부한다")
    void rejectsMissingClientSecret() {
        assertThatThrownBy(() -> new KakaoProperties(
                CLIENT_ID, " ", REDIRECT_URI, AUTHORIZE_URI, TOKEN_URI, USER_INFO_URI))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("client-secret");
    }

    @Test
    @DisplayName("리다이렉트 URI가 비어 있으면 거부한다")
    void rejectsBlankRedirectUri() {
        assertThatThrownBy(() -> new KakaoProperties(
                CLIENT_ID, CLIENT_SECRET, " ", AUTHORIZE_URI, TOKEN_URI, USER_INFO_URI))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("redirect-uri");
    }

    @Test
    @DisplayName("카카오 엔드포인트 URI가 하나라도 없으면 거부한다")
    void rejectsMissingEndpointUris() {
        assertThatThrownBy(() -> new KakaoProperties(
                CLIENT_ID, CLIENT_SECRET, REDIRECT_URI, null, TOKEN_URI, USER_INFO_URI))
                .hasMessageContaining("authorize-uri");
        assertThatThrownBy(() -> new KakaoProperties(
                CLIENT_ID, CLIENT_SECRET, REDIRECT_URI, AUTHORIZE_URI, null, USER_INFO_URI))
                .hasMessageContaining("token-uri");
        assertThatThrownBy(() -> new KakaoProperties(
                CLIENT_ID, CLIENT_SECRET, REDIRECT_URI, AUTHORIZE_URI, TOKEN_URI, null))
                .hasMessageContaining("user-info-uri");
    }
}
