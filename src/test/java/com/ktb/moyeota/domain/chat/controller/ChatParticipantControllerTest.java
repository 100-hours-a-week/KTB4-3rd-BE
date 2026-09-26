package com.ktb.moyeota.domain.chat.controller;

import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ktb.moyeota.domain.chat.dto.ChatLeaveResponse;
import com.ktb.moyeota.domain.chat.dto.ChatParticipateResponse;
import com.ktb.moyeota.domain.chat.exception.ChatErrorCode;
import com.ktb.moyeota.domain.chat.service.ChatParticipationService;
import com.ktb.moyeota.global.config.ClockConfig;
import com.ktb.moyeota.global.config.CorsConfig;
import com.ktb.moyeota.global.config.CorsProperties;
import com.ktb.moyeota.global.config.WebConfig;
import com.ktb.moyeota.global.exception.BusinessException;
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
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

@WebMvcTest(controllers = ChatParticipantController.class)
@Import({SecurityConfig.class, CorsConfig.class, JwtConfig.class, ClockConfig.class, WebConfig.class,
        AuthUserArgumentResolver.class, SignupPrincipalArgumentResolver.class, UploadScopeArgumentResolver.class,
        ApiAuthenticationEntryPoint.class, ApiAccessDeniedHandler.class, GlobalExceptionHandler.class})
@EnableConfigurationProperties({AuthProperties.class, CorsProperties.class})
class ChatParticipantControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ChatParticipationService chatParticipationService;

    @MockitoBean
    private SignupSessionAuthenticator signupSessionAuthenticator;

    @Test
    @DisplayName("참여하면 201과 Location, 참여 데이터 응답을 내린다")
    void participate() throws Exception {
        given(chatParticipationService.participate(42L, 10L))
                .willReturn(new ChatParticipateResponse(100L, 30L));

        mockMvc.perform(post("/api/companion-posts/10/participants").with(member()))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "/api/companion-posts/10/participants/100"))
                .andExpect(jsonPath("$.message").value("채팅방에 참여했습니다"))
                .andExpect(jsonPath("$.data.companion_participant_id").value(100))
                .andExpect(jsonPath("$.data.chat_room_id").value(30));
    }

    @Test
    @DisplayName("이미 참여 중이면 409 ALREADY_PARTICIPATING이다")
    void alreadyParticipating() throws Exception {
        given(chatParticipationService.participate(42L, 10L))
                .willThrow(new BusinessException(ChatErrorCode.ALREADY_PARTICIPATING));

        mockMvc.perform(post("/api/companion-posts/10/participants").with(member()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("ALREADY_PARTICIPATING"));
    }

    @Test
    @DisplayName("참여할 수 없는 동행이면 409 COMPANION_NOT_JOINABLE이다")
    void notJoinable() throws Exception {
        given(chatParticipationService.participate(42L, 10L))
                .willThrow(new BusinessException(ChatErrorCode.COMPANION_NOT_JOINABLE));

        mockMvc.perform(post("/api/companion-posts/10/participants").with(member()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("COMPANION_NOT_JOINABLE"));
    }

    @Test
    @DisplayName("존재하지 않는 동행이면 404 COMPANION_NOT_FOUND다")
    void companionNotFound() throws Exception {
        given(chatParticipationService.participate(42L, 10L))
                .willThrow(new BusinessException(ChatErrorCode.COMPANION_NOT_FOUND));

        mockMvc.perform(post("/api/companion-posts/10/participants").with(member()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("COMPANION_NOT_FOUND"));
    }

    @Test
    @DisplayName("액세스 토큰이 없으면 401이고 서비스를 부르지 않는다")
    void participateAnonymous() throws Exception {
        mockMvc.perform(post("/api/companion-posts/10/participants"))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(chatParticipationService);
    }

    @Test
    @DisplayName("나가면 204 No Content다")
    void leave() throws Exception {
        given(chatParticipationService.leave(42L, 10L)).willReturn(new ChatLeaveResponse(30L));

        mockMvc.perform(delete("/api/companion-posts/10/participants/me").with(member()))
                .andExpect(status().isNoContent());

        verify(chatParticipationService).leave(42L, 10L);
    }

    @Test
    @DisplayName("참여 중이 아니면 404 NOT_PARTICIPATING이다")
    void notParticipating() throws Exception {
        doThrow(new BusinessException(ChatErrorCode.NOT_PARTICIPATING))
                .when(chatParticipationService).leave(42L, 10L);

        mockMvc.perform(delete("/api/companion-posts/10/participants/me").with(member()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("NOT_PARTICIPATING"));
    }

    private static RequestPostProcessor member() {
        return jwt().jwt(builder -> builder.subject("42")).authorities(Authority.USER);
    }
}
