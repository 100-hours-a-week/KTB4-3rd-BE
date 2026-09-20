package com.ktb.moyeota.domain.auth.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ktb.moyeota.domain.auth.model.AuthorizeRedirect;
import com.ktb.moyeota.domain.auth.model.OAuthProvider;
import com.ktb.moyeota.domain.auth.service.OAuthLoginService;
import com.ktb.moyeota.global.config.ClockConfig;
import com.ktb.moyeota.global.config.CorsConfig;
import com.ktb.moyeota.global.config.CorsProperties;
import com.ktb.moyeota.global.exception.GlobalExceptionHandler;
import com.ktb.moyeota.global.security.AuthProperties;
import com.ktb.moyeota.global.security.SecurityConfig;
import com.ktb.moyeota.global.security.cookie.AuthCookies;
import com.ktb.moyeota.global.security.handler.ApiAccessDeniedHandler;
import com.ktb.moyeota.global.security.handler.ApiAuthenticationEntryPoint;
import com.ktb.moyeota.global.security.jwt.JwtConfig;
import java.net.URI;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(controllers = OAuthLoginController.class)
@Import({SecurityConfig.class, CorsConfig.class, JwtConfig.class, ClockConfig.class,
        AuthCookies.class, ApiAuthenticationEntryPoint.class, ApiAccessDeniedHandler.class,
        GlobalExceptionHandler.class})
@EnableConfigurationProperties({AuthProperties.class, CorsProperties.class})
class OAuthLoginControllerTest {

    private static final String OAUTH_STATE = "oauth_state";
    private static final String STATE = "state-value";
    private static final String AUTHORIZE_LOCATION =
            "https://kauth.kakao.com/oauth/authorize?client_id=test&state=" + STATE;

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private OAuthLoginService oAuthLoginService;

    @Test
    @DisplayName("토큰 없이도 카카오 인가 화면으로 302 리다이렉트한다")
    void redirectsToKakaoWithoutToken() throws Exception {
        givenKakaoRedirect();

        mockMvc.perform(get("/auth/kakao/login"))
                .andExpect(status().isFound())
                .andExpect(header().string(HttpHeaders.LOCATION, AUTHORIZE_LOCATION));
    }

    @Test
    @DisplayName("state는 5분짜리 HttpOnly 쿠키로 내려간다")
    void setsStateCookie() throws Exception {
        givenKakaoRedirect();

        mockMvc.perform(get("/auth/kakao/login"))
                .andExpect(cookie().value(OAUTH_STATE, STATE))
                .andExpect(cookie().maxAge(OAUTH_STATE, 300))
                .andExpect(cookie().path(OAUTH_STATE, "/auth"))
                .andExpect(cookie().httpOnly(OAUTH_STATE, true));
    }

    @Test
    @DisplayName("state 쿠키는 SameSite=Lax다")
    void stateCookieIsSameSiteLax() throws Exception {
        givenKakaoRedirect();

        mockMvc.perform(get("/auth/kakao/login"))
                .andExpect(result -> assertThat(result.getResponse().getHeader(HttpHeaders.SET_COOKIE))
                        .contains("SameSite=Lax"));
    }

    @Test
    @DisplayName("지원하지 않는 공급자는 404다")
    void unknownProviderIsNotFound() throws Exception {
        mockMvc.perform(get("/auth/naver/login"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("ENDPOINT_NOT_FOUND"));

        verifyNoInteractions(oAuthLoginService);
    }

    @Test
    @DisplayName("공급자 이름은 대소문자를 구분한다")
    void providerIsCaseSensitive() throws Exception {
        mockMvc.perform(get("/auth/KAKAO/login"))
                .andExpect(status().isNotFound());
    }

    private void givenKakaoRedirect() {
        given(oAuthLoginService.buildAuthorizeRedirect(OAuthProvider.KAKAO))
                .willReturn(new AuthorizeRedirect(URI.create(AUTHORIZE_LOCATION), STATE));
    }
}
