package com.ktb.moyeota.domain.user.controller;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ktb.moyeota.domain.user.dto.PushTokenRequest;
import com.ktb.moyeota.domain.user.service.PushTokenService;
import com.ktb.moyeota.global.config.ClockConfig;
import com.ktb.moyeota.global.config.CorsConfig;
import com.ktb.moyeota.global.config.CorsProperties;
import com.ktb.moyeota.global.config.WebConfig;
import com.ktb.moyeota.global.exception.GlobalExceptionHandler;
import com.ktb.moyeota.global.security.AuthProperties;
import com.ktb.moyeota.global.security.Authority;
import com.ktb.moyeota.global.security.SecurityConfig;
import com.ktb.moyeota.global.security.handler.ApiAccessDeniedHandler;
import com.ktb.moyeota.global.security.handler.ApiAuthenticationEntryPoint;
import com.ktb.moyeota.global.security.jwt.JwtConfig;
import com.ktb.moyeota.global.security.resolver.AuthUserArgumentResolver;
import com.ktb.moyeota.global.security.resolver.SignupPrincipalArgumentResolver;
import com.ktb.moyeota.global.security.resolver.UploadScopeArgumentResolver;
import com.ktb.moyeota.global.security.signup.SignupSessionAuthenticator;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

@WebMvcTest(controllers = PushTokenController.class)
@Import({SecurityConfig.class, CorsConfig.class, JwtConfig.class, ClockConfig.class, WebConfig.class,
        AuthUserArgumentResolver.class, SignupPrincipalArgumentResolver.class, UploadScopeArgumentResolver.class,
        ApiAuthenticationEntryPoint.class, ApiAccessDeniedHandler.class, GlobalExceptionHandler.class})
@EnableConfigurationProperties({AuthProperties.class, CorsProperties.class})
class PushTokenControllerTest {

    private static final Long USER_ID = 42L;
    private static final String URL = "/api/push-tokens";
    private static final String BODY = "{\"token\": \"fcm-token\"}";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private PushTokenService pushTokenService;

    @MockitoBean
    private SignupSessionAuthenticator signupSessionAuthenticator;

    @Nested
    @DisplayName("PUT /api/push-tokens")
    class Register {

        @Test
        @DisplayName("등록/갱신하면 204이고 본문이 없다")
        void noContent() throws Exception {
            mockMvc.perform(put(URL).with(member()).contentType("application/json").content(BODY))
                    .andExpect(status().isNoContent())
                    .andExpect(content().string(""));

            verify(pushTokenService).register(eq(USER_ID), eq(new PushTokenRequest("fcm-token")));
        }

        @ParameterizedTest
        @ValueSource(strings = {"{\"token\": \"\"}", "{\"token\": \"   \"}", "{}"})
        @DisplayName("토큰이 비어 있으면 422 VALIDATION_ERROR(REQUIRED)이고 서비스를 호출하지 않는다")
        void blank(String body) throws Exception {
            mockMvc.perform(put(URL).with(member()).contentType("application/json").content(body))
                    .andExpect(status().isUnprocessableContent())
                    .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"))
                    .andExpect(jsonPath("$.error.field").value("token"))
                    .andExpect(jsonPath("$.error.details[0].field").value("token"))
                    .andExpect(jsonPath("$.error.details[0].reason").value("REQUIRED"));

            verifyNoInteractions(pushTokenService);
        }

        @Test
        @DisplayName("토큰이 500자를 넘으면 422 VALIDATION_ERROR(LENGTH_OUT_OF_RANGE)이고 서비스를 호출하지 않는다")
        void tooLong() throws Exception {
            String body = "{\"token\": \"" + "a".repeat(501) + "\"}";

            mockMvc.perform(put(URL).with(member()).contentType("application/json").content(body))
                    .andExpect(status().isUnprocessableContent())
                    .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"))
                    .andExpect(jsonPath("$.error.details[0].field").value("token"))
                    .andExpect(jsonPath("$.error.details[0].reason").value("LENGTH_OUT_OF_RANGE"));

            verifyNoInteractions(pushTokenService);
        }

        @Test
        @DisplayName("토큰에 ASCII가 아닌 문자가 있으면 422 VALIDATION_ERROR(INVALID_FORMAT)이고 서비스를 호출하지 않는다")
        void nonAscii() throws Exception {
            mockMvc.perform(put(URL).with(member()).contentType("application/json").content("{\"token\": \"ㅊ\"}"))
                    .andExpect(status().isUnprocessableContent())
                    .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"))
                    .andExpect(jsonPath("$.error.details[0].field").value("token"))
                    .andExpect(jsonPath("$.error.details[0].reason").value("INVALID_FORMAT"));

            verifyNoInteractions(pushTokenService);
        }

        @Test
        @DisplayName("body가 없으면 400이고 서비스를 호출하지 않는다")
        void noBody() throws Exception {
            mockMvc.perform(put(URL).with(member()).contentType("application/json"))
                    .andExpect(status().isBadRequest());

            verifyNoInteractions(pushTokenService);
        }

        @Test
        @DisplayName("액세스 토큰이 없으면 401이고 서비스를 호출하지 않는다")
        void anonymous() throws Exception {
            mockMvc.perform(put(URL).contentType("application/json").content(BODY))
                    .andExpect(status().isUnauthorized());

            verifyNoInteractions(pushTokenService);
        }
    }

    @Nested
    @DisplayName("DELETE /api/push-tokens")
    class Delete {

        @Test
        @DisplayName("삭제하면 204이고 본문이 없다")
        void noContent() throws Exception {
            mockMvc.perform(delete(URL).with(member()).contentType("application/json").content(BODY))
                    .andExpect(status().isNoContent())
                    .andExpect(content().string(""));

            verify(pushTokenService).delete(eq(USER_ID), eq(new PushTokenRequest("fcm-token")));
        }

        @ParameterizedTest
        @ValueSource(strings = {"{\"token\": \"\"}", "{}"})
        @DisplayName("토큰이 비어 있으면 422 VALIDATION_ERROR(REQUIRED)이고 서비스를 호출하지 않는다")
        void blank(String body) throws Exception {
            mockMvc.perform(delete(URL).with(member()).contentType("application/json").content(body))
                    .andExpect(status().isUnprocessableContent())
                    .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"))
                    .andExpect(jsonPath("$.error.details[0].field").value("token"))
                    .andExpect(jsonPath("$.error.details[0].reason").value("REQUIRED"));

            verifyNoInteractions(pushTokenService);
        }

        @Test
        @DisplayName("토큰이 500자를 넘으면 422 VALIDATION_ERROR(LENGTH_OUT_OF_RANGE)이고 서비스를 호출하지 않는다")
        void tooLong() throws Exception {
            String body = "{\"token\": \"" + "a".repeat(501) + "\"}";

            mockMvc.perform(delete(URL).with(member()).contentType("application/json").content(body))
                    .andExpect(status().isUnprocessableContent())
                    .andExpect(jsonPath("$.error.details[0].reason").value("LENGTH_OUT_OF_RANGE"));

            verifyNoInteractions(pushTokenService);
        }

        @Test
        @DisplayName("액세스 토큰이 없으면 401이고 서비스를 호출하지 않는다")
        void anonymous() throws Exception {
            mockMvc.perform(delete(URL).contentType("application/json").content(BODY))
                    .andExpect(status().isUnauthorized());

            verifyNoInteractions(pushTokenService);
        }
    }

    private static RequestPostProcessor member() {
        return jwt().jwt(builder -> builder.subject(String.valueOf(USER_ID))).authorities(Authority.USER);
    }
}
