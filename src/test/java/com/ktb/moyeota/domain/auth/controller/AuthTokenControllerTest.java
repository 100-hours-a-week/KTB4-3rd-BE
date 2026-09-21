package com.ktb.moyeota.domain.auth.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ktb.moyeota.domain.auth.model.ReissueResult;
import com.ktb.moyeota.domain.auth.service.AuthSessionService;
import com.ktb.moyeota.global.config.ClockConfig;
import com.ktb.moyeota.global.config.CorsConfig;
import com.ktb.moyeota.global.config.CorsProperties;
import com.ktb.moyeota.global.exception.GlobalExceptionHandler;
import com.ktb.moyeota.global.security.AuthProperties;
import com.ktb.moyeota.global.security.SecurityConfig;
import com.ktb.moyeota.global.security.cookie.AuthCookies;
import com.ktb.moyeota.global.security.handler.ApiAccessDeniedHandler;
import com.ktb.moyeota.global.security.handler.ApiAuthenticationEntryPoint;
import com.ktb.moyeota.global.security.jwt.AccessToken;
import com.ktb.moyeota.global.security.jwt.JwtConfig;
import com.ktb.moyeota.global.security.signup.SignupSessionAuthenticator;
import jakarta.servlet.http.Cookie;
import java.time.Duration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@WebMvcTest(controllers = AuthTokenController.class)
@Import({SecurityConfig.class, CorsConfig.class, JwtConfig.class, ClockConfig.class,
        AuthCookies.class, ApiAuthenticationEntryPoint.class, ApiAccessDeniedHandler.class,
        GlobalExceptionHandler.class})
@EnableConfigurationProperties({AuthProperties.class, CorsProperties.class})
class AuthTokenControllerTest {

    private static final String REFRESH_TOKEN = "refresh_token";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private SignupSessionAuthenticator signupSessionAuthenticator;

    @MockitoBean
    private AuthSessionService authSessionService;

    @Test
    @DisplayName("로그아웃은 204와 함께 쿠키를 지운다")
    void logoutClearsCookie() throws Exception {
        mockMvc.perform(delete("/auth/sessions").cookie(new Cookie(REFRESH_TOKEN, "any-value")))
                .andExpect(status().isNoContent())
                .andExpect(cookie().maxAge(REFRESH_TOKEN, 0))
                .andExpect(cookie().path(REFRESH_TOKEN, "/auth"));

        verify(authSessionService).revokeSession("any-value");
    }

    @Test
    @DisplayName("쿠키 없이 로그아웃해도 204다")
    void logoutIsIdempotent() throws Exception {
        mockMvc.perform(delete("/auth/sessions"))
                .andExpect(status().isNoContent())
                .andExpect(cookie().maxAge(REFRESH_TOKEN, 0));

        verify(authSessionService).revokeSession(null);
    }

    @Test
    @DisplayName("회전하면 새 리프레시 토큰이 쿠키로 내려간다")
    void rotatedSetsCookie() throws Exception {
        given(authSessionService.reissue(any())).willReturn(new ReissueResult.Rotated(
                new AccessToken("access-value", 1800), "rotated-value", Duration.ofDays(7)));

        mockMvc.perform(post("/auth/tokens").cookie(new Cookie(REFRESH_TOKEN, "old-value")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.access_token").value("access-value"))
                .andExpect(jsonPath("$.data.expires_in").value(1800))
                .andExpect(cookie().value(REFRESH_TOKEN, "rotated-value"))
                .andExpect(cookie().httpOnly(REFRESH_TOKEN, true))
                .andExpect(cookie().path(REFRESH_TOKEN, "/auth"))
                .andExpect(result -> assertThat(result.getResponse().getHeader(HttpHeaders.SET_COOKIE))
                        .contains("SameSite=Lax"));
    }

    @Test
    @DisplayName("grace면 액세스 토큰만 주고 쿠키를 건드리지 않는다")
    void graceDoesNotSetCookie() throws Exception {
        given(authSessionService.reissue(any()))
                .willReturn(new ReissueResult.Graced(new AccessToken("access-value", 1800)));

        mockMvc.perform(post("/auth/tokens").cookie(new Cookie(REFRESH_TOKEN, "old-value")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.access_token").value("access-value"))
                .andExpect(header().doesNotExist(HttpHeaders.SET_COOKIE));
    }

    @Test
    @DisplayName("거부되면 401과 함께 쿠키를 지운다")
    void rejectedClearsCookie() throws Exception {
        given(authSessionService.reissue(any())).willReturn(new ReissueResult.Rejected());

        mockMvc.perform(post("/auth/tokens").cookie(new Cookie(REFRESH_TOKEN, "stale-value")))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("UNAUTHORIZED"))
                .andExpect(cookie().maxAge(REFRESH_TOKEN, 0));
    }

    @Test
    @DisplayName("쿠키 없이 호출해도 인가에 막히지 않고 서비스가 판단한다")
    void reachesControllerWithoutCookie() throws Exception {
        given(authSessionService.reissue(any())).willReturn(new ReissueResult.Rejected());

        mockMvc.perform(post("/auth/tokens"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("UNAUTHORIZED"));

        verify(authSessionService).reissue(null);
    }


}
