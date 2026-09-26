package com.ktb.moyeota.domain.chat.controller;

import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ktb.moyeota.domain.chat.dto.MessageItem;
import com.ktb.moyeota.domain.chat.dto.MessageListResponse;
import com.ktb.moyeota.domain.chat.entity.MessageType;
import com.ktb.moyeota.domain.chat.exception.ChatErrorCode;
import com.ktb.moyeota.domain.chat.service.ChatMessageService;
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
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

@WebMvcTest(controllers = ChatMessageController.class)
@Import({SecurityConfig.class, CorsConfig.class, JwtConfig.class, ClockConfig.class, WebConfig.class,
        AuthUserArgumentResolver.class, SignupPrincipalArgumentResolver.class, UploadScopeArgumentResolver.class,
        ApiAuthenticationEntryPoint.class, ApiAccessDeniedHandler.class, GlobalExceptionHandler.class})
@EnableConfigurationProperties({AuthProperties.class, CorsProperties.class})
class ChatMessageControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ChatMessageService chatMessageService;

    @MockitoBean
    private SignupSessionAuthenticator signupSessionAuthenticator;

    @Test
    @DisplayName("채팅방 메시지 목록을 조회한다")
    void list() throws Exception {
        MessageItem item = new MessageItem(
                900L, MessageType.TEXT,
                new MessageItem.Sender(7L, "우림", "https://img"),
                null, null, "안녕하세요", LocalDateTime.of(2026, 9, 5, 9, 0));
        given(chatMessageService.findMessages(42L, 30L, null))
                .willReturn(new MessageListResponse(List.of(item), null));

        mockMvc.perform(get("/api/chat-rooms/30/messages").with(member()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("조회에 성공했습니다"))
                .andExpect(jsonPath("$.data.items[0].id").value(900))
                .andExpect(jsonPath("$.data.items[0].type").value("TEXT"))
                .andExpect(jsonPath("$.data.items[0].sender.nickname").value("우림"))
                .andExpect(jsonPath("$.data.items[0].content").value("안녕하세요"));
    }

    @Test
    @DisplayName("cursor 쿼리 파라미터를 그대로 서비스에 전달한다")
    void listWithCursor() throws Exception {
        given(chatMessageService.findMessages(42L, 30L, "v1.abc"))
                .willReturn(new MessageListResponse(List.of(), null));

        mockMvc.perform(get("/api/chat-rooms/30/messages").queryParam("cursor", "v1.abc").with(member()))
                .andExpect(status().isOk());

        verify(chatMessageService).findMessages(42L, 30L, "v1.abc");
    }

    @Test
    @DisplayName("참여 중인 채팅방이 아니면 404 CHATROOM_NOT_FOUND다")
    void notParticipating() throws Exception {
        given(chatMessageService.findMessages(42L, 30L, null))
                .willThrow(new BusinessException(ChatErrorCode.CHATROOM_NOT_FOUND));

        mockMvc.perform(get("/api/chat-rooms/30/messages").with(member()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("CHATROOM_NOT_FOUND"));
    }

    @Test
    @DisplayName("액세스 토큰이 없으면 401이고 서비스를 부르지 않는다")
    void anonymous() throws Exception {
        mockMvc.perform(get("/api/chat-rooms/30/messages"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("UNAUTHORIZED"));

        verifyNoInteractions(chatMessageService);
    }

    private static RequestPostProcessor member() {
        return jwt().jwt(builder -> builder.subject("42")).authorities(Authority.USER);
    }
}
