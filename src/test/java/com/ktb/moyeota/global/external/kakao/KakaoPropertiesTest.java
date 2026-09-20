package com.ktb.moyeota.global.external.kakao;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class KakaoPropertiesTest {

    private static final String CLIENT_ID = "test-kakao-client-id";
    private static final String REDIRECT_URI = "http://localhost:8080/auth/kakao/callback";
    private static final String AUTHORIZE_URI = "https://kauth.kakao.com/oauth/authorize";

    @Test
    @DisplayName("세 값이 모두 있으면 통과한다")
    void acceptsCompleteValues() {
        assertThatCode(() -> new KakaoProperties(CLIENT_ID, REDIRECT_URI, AUTHORIZE_URI))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("REST API 키가 없으면 기동 시점에 거부한다")
    void rejectsMissingClientId() {
        assertThatThrownBy(() -> new KakaoProperties(null, REDIRECT_URI, AUTHORIZE_URI))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("client-id");
    }

    @Test
    @DisplayName("리다이렉트 URI가 비어 있으면 거부한다")
    void rejectsBlankRedirectUri() {
        assertThatThrownBy(() -> new KakaoProperties(CLIENT_ID, " ", AUTHORIZE_URI))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("redirect-uri");
    }

    @Test
    @DisplayName("인가 URI가 없으면 거부한다")
    void rejectsMissingAuthorizeUri() {
        assertThatThrownBy(() -> new KakaoProperties(CLIENT_ID, REDIRECT_URI, null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("authorize-uri");
    }
}
