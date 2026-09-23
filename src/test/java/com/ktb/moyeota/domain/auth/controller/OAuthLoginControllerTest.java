package com.ktb.moyeota.domain.auth.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ktb.moyeota.domain.auth.error.OAuthLoginError;
import com.ktb.moyeota.domain.auth.model.AuthorizeRedirect;
import com.ktb.moyeota.domain.auth.model.CallbackParams;
import com.ktb.moyeota.domain.auth.model.OAuthCallbackResult;
import com.ktb.moyeota.domain.auth.model.OAuthProvider;
import com.ktb.moyeota.domain.auth.service.OAuthLoginService;
import com.ktb.moyeota.global.config.ClockConfig;
import com.ktb.moyeota.global.config.CorsConfig;
import com.ktb.moyeota.global.config.CorsProperties;
import com.ktb.moyeota.global.config.OAuthProperties;
import com.ktb.moyeota.global.exception.GlobalExceptionHandler;
import com.ktb.moyeota.global.security.AuthProperties;
import com.ktb.moyeota.global.security.SecurityConfig;
import com.ktb.moyeota.global.security.cookie.AuthCookies;
import com.ktb.moyeota.global.security.handler.ApiAccessDeniedHandler;
import com.ktb.moyeota.global.security.handler.ApiAuthenticationEntryPoint;
import com.ktb.moyeota.global.security.jwt.JwtConfig;
import com.ktb.moyeota.global.security.signup.SignupSessionAuthenticator;
import jakarta.servlet.http.Cookie;
import java.net.URI;
import java.time.Duration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
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
@EnableConfigurationProperties({AuthProperties.class, CorsProperties.class, OAuthProperties.class})
class OAuthLoginControllerTest {

    private static final String OAUTH_STATE = "oauth_state";
    private static final String REFRESH_TOKEN = "refresh_token";
    private static final String SIGNUP_TOKEN = "signup_token";
    private static final String STATE = "state-value";
    private static final String FRONT_CALLBACK = "http://localhost:3000/auth/callback";
    private static final String AUTHORIZE_LOCATION =
            "https://kauth.kakao.com/oauth/authorize?client_id=test&state=" + STATE;

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private SignupSessionAuthenticator signupSessionAuthenticator;

    @MockitoBean
    private OAuthLoginService oAuthLoginService;

    @Nested
    @DisplayName("로그인 진입")
    class StartLogin {

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

    @Nested
    @DisplayName("콜백")
    class Callback {

        @Test
        @DisplayName("쿼리와 state 쿠키를 그대로 서비스에 넘긴다")
        void passesParamsToService() throws Exception {
            givenResult(new OAuthCallbackResult.Failed(OAuthLoginError.INVALID_STATE));

            mockMvc.perform(get("/auth/kakao/callback")
                    .param("code", "auth-code")
                    .param("state", STATE)
                    .cookie(new Cookie(OAUTH_STATE, "cookie-state")));

            verify(oAuthLoginService).handleCallback(
                    OAuthProvider.KAKAO, new CallbackParams("auth-code", STATE, null, "cookie-state"));
        }

        @Test
        @DisplayName("기존 회원은 status=ok로 돌아가고 리프레시 쿠키를 받는다")
        void existingMember() throws Exception {
            givenResult(new OAuthCallbackResult.Existing("refresh-value", Duration.ofDays(7)));

            mockMvc.perform(get("/auth/kakao/callback").param("code", "c").param("state", STATE))
                    .andExpect(status().isFound())
                    .andExpect(header().string(HttpHeaders.LOCATION, FRONT_CALLBACK + "?status=ok"))
                    .andExpect(content().string(""))
                    .andExpect(cookie().value(REFRESH_TOKEN, "refresh-value"))
                    .andExpect(cookie().maxAge(REFRESH_TOKEN, 604800))
                    .andExpect(cookie().path(REFRESH_TOKEN, "/auth"))
                    .andExpect(cookie().httpOnly(REFRESH_TOKEN, true))
                    .andExpect(cookie().doesNotExist(SIGNUP_TOKEN));
        }

        @Test
        @DisplayName("신규는 status=signup_required로 돌아가고 15분짜리 회원가입 쿠키를 받는다")
        void newcomer() throws Exception {
            givenResult(new OAuthCallbackResult.SignupRequired("signup-value", Duration.ofMinutes(15)));

            mockMvc.perform(get("/auth/kakao/callback").param("code", "c").param("state", STATE))
                    .andExpect(status().isFound())
                    .andExpect(header().string(
                            HttpHeaders.LOCATION, FRONT_CALLBACK + "?status=signup_required"))
                    .andExpect(cookie().value(SIGNUP_TOKEN, "signup-value"))
                    .andExpect(cookie().maxAge(SIGNUP_TOKEN, 900))
                    .andExpect(cookie().path(SIGNUP_TOKEN, "/"))
                    .andExpect(cookie().httpOnly(SIGNUP_TOKEN, true))
                    .andExpect(cookie().doesNotExist(REFRESH_TOKEN));
        }

        @Test
        @DisplayName("실패는 JSON이 아니라 error 쿼리로 전달하고 자격 쿠키를 주지 않는다")
        void failure() throws Exception {
            givenResult(new OAuthCallbackResult.Failed(OAuthLoginError.OAUTH_UNAVAILABLE));

            mockMvc.perform(get("/auth/kakao/callback").param("code", "c").param("state", STATE))
                    .andExpect(status().isFound())
                    .andExpect(header().string(
                            HttpHeaders.LOCATION, FRONT_CALLBACK + "?error=OAUTH_UNAVAILABLE"))
                    .andExpect(content().string(""))
                    .andExpect(cookie().doesNotExist(REFRESH_TOKEN))
                    .andExpect(cookie().doesNotExist(SIGNUP_TOKEN));
        }

        @Test
        @DisplayName("결과와 무관하게 state 쿠키를 지운다")
        void alwaysExpiresStateCookie() throws Exception {
            givenResult(new OAuthCallbackResult.Existing("refresh-value", Duration.ofDays(7)));
            mockMvc.perform(get("/auth/kakao/callback"))
                    .andExpect(cookie().maxAge(OAUTH_STATE, 0))
                    .andExpect(cookie().path(OAUTH_STATE, "/auth"));

            givenResult(new OAuthCallbackResult.Failed(OAuthLoginError.INVALID_STATE));
            mockMvc.perform(get("/auth/kakao/callback"))
                    .andExpect(cookie().maxAge(OAUTH_STATE, 0));
        }

        @Test
        @DisplayName("쿼리가 하나도 없어도 400이 아니라 302로 응답한다")
        void missingParamsStillRedirect() throws Exception {
            givenResult(new OAuthCallbackResult.Failed(OAuthLoginError.INVALID_STATE));

            mockMvc.perform(get("/auth/kakao/callback"))
                    .andExpect(status().isFound())
                    .andExpect(header().string(
                            HttpHeaders.LOCATION, FRONT_CALLBACK + "?error=INVALID_STATE"));
        }

        @Test
        @DisplayName("지원하지 않는 공급자는 404다")
        void unknownProviderIsNotFound() throws Exception {
            mockMvc.perform(get("/auth/naver/callback").param("code", "c").param("state", STATE))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.error.code").value("ENDPOINT_NOT_FOUND"));

            verifyNoInteractions(oAuthLoginService);
        }

        private void givenResult(OAuthCallbackResult result) {
            given(oAuthLoginService.handleCallback(eq(OAuthProvider.KAKAO), any())).willReturn(result);
        }
    }
}
