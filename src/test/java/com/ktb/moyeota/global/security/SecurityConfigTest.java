package com.ktb.moyeota.global.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ktb.moyeota.global.config.ClockConfig;
import com.ktb.moyeota.global.config.CorsConfig;
import com.ktb.moyeota.global.config.CorsProperties;
import com.ktb.moyeota.global.exception.GlobalExceptionHandler;
import com.ktb.moyeota.global.security.handler.ApiAccessDeniedHandler;
import com.ktb.moyeota.global.security.handler.ApiAuthenticationEntryPoint;
import com.ktb.moyeota.global.security.jwt.AccessTokenProvider;
import com.ktb.moyeota.global.security.jwt.JwtConfig;
import com.ktb.moyeota.global.security.resolver.AuthUser;
import com.ktb.moyeota.global.security.signup.SignupSessionAuthenticator;
import jakarta.servlet.DispatcherType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultMatcher;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@WebMvcTest(controllers = SecurityConfigTest.MatrixController.class)
@Import({SecurityConfig.class, CorsConfig.class, JwtConfig.class, ClockConfig.class,
        AccessTokenProvider.class, ApiAuthenticationEntryPoint.class, ApiAccessDeniedHandler.class,
        GlobalExceptionHandler.class, SecurityConfigTest.MatrixController.class})
@EnableConfigurationProperties({AuthProperties.class, CorsProperties.class})
class SecurityConfigTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private AccessTokenProvider accessTokenProvider;

    @MockitoBean
    private SignupSessionAuthenticator signupSessionAuthenticator;

    @Nested
    @DisplayName("그 외 모든 경로")
    class ProtectedPaths {

        @Test
        @DisplayName("USER 권한이면 통과한다")
        void userAuthorityPasses() throws Exception {
            mockMvc.perform(get("/probe/me").with(user()))
                    .andExpect(status().isOk())
                    .andExpect(content().string("42"));
        }

        @Test
        @DisplayName("SIGNUP 권한으로는 접근할 수 없다")
        void signupAuthorityIsForbidden() throws Exception {
            mockMvc.perform(get("/probe/me").with(signup()))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.error.code").value("FORBIDDEN"));
        }

        @Test
        @DisplayName("토큰이 없으면 401이다")
        void anonymousIsUnauthorized() throws Exception {
            mockMvc.perform(get("/probe/me"))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.error.code").value("UNAUTHORIZED"))
                    .andExpect(jsonPath("$.message").value("인증이 필요합니다."));
        }
    }

    @Nested
    @DisplayName("에러 디스패치 경로")
    class ErrorPath {

        @Test
        @DisplayName("ERROR 디스패치로 들어온 /error는 인증 없이도 인가에 막히지 않는다")
        void errorDispatchIsNotBlocked() throws Exception {
            mockMvc.perform(get("/error").with(request -> {
                        request.setDispatcherType(DispatcherType.ERROR);
                        return request;
                    }))
                    .andExpect(notBlockedByAuthorization());
        }

        @Test
        @DisplayName("일반 요청으로 들어온 /error도 인가에 막히지 않는다")
        void errorPathIsNotBlocked() throws Exception {
            mockMvc.perform(get("/error"))
                    .andExpect(notBlockedByAuthorization());
        }
    }

    @Nested
    @DisplayName("토큰 검증 실패")
    class InvalidToken {

        @Test
        @DisplayName("JWT 형식이 아닌 토큰은 401이다")
        void malformedTokenIsUnauthorized() throws Exception {
            mockMvc.perform(get("/probe/me").header(HttpHeaders.AUTHORIZATION, "Bearer not-a-jwt"))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.error.code").value("UNAUTHORIZED"));
        }

        @Test
        @DisplayName("실제 발급한 토큰은 통과한다")
        void issuedTokenPasses() throws Exception {
            String token = accessTokenProvider.issue(7L).value();

            mockMvc.perform(get("/probe/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                    .andExpect(status().isOk())
                    .andExpect(content().string("7"));
        }
    }

    private static ResultMatcher notBlockedByAuthorization() {
        return result -> assertThat(result.getResponse().getStatus())
                .isNotIn(HttpStatus.UNAUTHORIZED.value(), HttpStatus.FORBIDDEN.value());
    }

    private static RequestPostProcessor user() {
        return jwt().jwt(builder -> builder.subject("42"))
                .authorities(Authority.USER);
    }

    private static RequestPostProcessor signup() {
        return jwt().jwt(builder -> builder.subject("42"))
                .authorities(Authority.SIGNUP);
    }

    @RestController
    static class MatrixController {

        @GetMapping("/probe/me")
        String me(@AuthUser Long userId) {
            return String.valueOf(userId);
        }
    }
}
