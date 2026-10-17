package com.ktb.moyeota.domain.chat.controller;

import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ktb.moyeota.domain.chat.service.ChatRoomService;
import com.ktb.moyeota.global.config.ClockConfig;
import com.ktb.moyeota.global.config.CorsConfig;
import com.ktb.moyeota.global.config.CorsProperties;
import com.ktb.moyeota.global.config.WebConfig;
import com.ktb.moyeota.global.exception.GlobalExceptionHandler;
import com.ktb.moyeota.global.security.AuthProperties;
import com.ktb.moyeota.global.security.InternalApiKeyFilter;
import com.ktb.moyeota.global.security.SecurityConfig;
import com.ktb.moyeota.global.security.handler.ApiAccessDeniedHandler;
import com.ktb.moyeota.global.security.handler.ApiAuthenticationEntryPoint;
import com.ktb.moyeota.global.security.jwt.AccessTokenProvider;
import com.ktb.moyeota.global.security.jwt.JwtConfig;
import com.ktb.moyeota.global.security.resolver.AuthUserArgumentResolver;
import com.ktb.moyeota.global.security.resolver.SignupPrincipalArgumentResolver;
import com.ktb.moyeota.global.security.resolver.UploadScopeArgumentResolver;
import com.ktb.moyeota.global.security.signup.SignupSessionAuthenticator;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(controllers = InternalChatRoomController.class)
@Import({SecurityConfig.class, CorsConfig.class, JwtConfig.class, ClockConfig.class, WebConfig.class,
        AuthUserArgumentResolver.class, SignupPrincipalArgumentResolver.class, UploadScopeArgumentResolver.class,
        ApiAuthenticationEntryPoint.class, ApiAccessDeniedHandler.class, GlobalExceptionHandler.class,
        AccessTokenProvider.class})
@EnableConfigurationProperties({AuthProperties.class, CorsProperties.class})
class InternalChatRoomControllerTest {

    private static final String PATH = "/internal/chat-rooms/6/participants/4";
    private static final String KEY = "test-only-internal-api-key";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private AccessTokenProvider accessTokenProvider;

    @MockitoBean
    private ChatRoomService chatRoomService;

    @MockitoBean
    private SignupSessionAuthenticator signupSessionAuthenticator;

    @Test
    @DisplayName("내부 키가 맞고 참여 중이면 is_participant true")
    void participant() throws Exception {
        given(chatRoomService.isActiveParticipant(6L, 4L)).willReturn(true);

        mockMvc.perform(get(PATH).header(InternalApiKeyFilter.HEADER, KEY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.is_participant").value(true));
    }

    @Test
    @DisplayName("내부 키가 맞고 참여 중이 아니면 is_participant false")
    void notParticipant() throws Exception {
        given(chatRoomService.isActiveParticipant(6L, 4L)).willReturn(false);

        mockMvc.perform(get(PATH).header(InternalApiKeyFilter.HEADER, KEY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.is_participant").value(false));
    }

    @Test
    @DisplayName("내부 키가 없으면 401")
    void missingKey() throws Exception {
        mockMvc.perform(get(PATH))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("UNAUTHORIZED"));
    }

    @Test
    @DisplayName("내부 키가 틀리면 401")
    void wrongKey() throws Exception {
        mockMvc.perform(get(PATH).header(InternalApiKeyFilter.HEADER, "wrong-key"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("로그인한 사용자 토큰만으로는 부를 수 없다")
    void userTokenWithoutKey() throws Exception {
        mockMvc.perform(get(PATH).header("Authorization", "Bearer " + accessTokenProvider.issue(4L).value()))
                .andExpect(status().isUnauthorized());
    }
}
