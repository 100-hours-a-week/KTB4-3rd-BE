package com.ktb.moyeota.domain.chat.controller;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
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
import com.ktb.moyeota.global.exception.CommonErrorCode;
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
                new MessageItem.Sender(7L, null, "우림", "https://img"),
                null, null, "안녕하세요", LocalDateTime.of(2026, 9, 5, 9, 0));
        given(chatMessageService.findMessages(42L, 30L, null, null, null))
                .willReturn(new MessageListResponse(List.of(item), null, null));

        mockMvc.perform(get("/api/chat-rooms/30/messages").with(member()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("조회에 성공했습니다"))
                .andExpect(jsonPath("$.data.items[0].id").value(900))
                .andExpect(jsonPath("$.data.items[0].type").value("TEXT"))
                .andExpect(jsonPath("$.data.items[0].sender.nickname").value("우림"))
                .andExpect(jsonPath("$.data.items[0].sender.name").doesNotExist())
                .andExpect(jsonPath("$.data.items[0].content").value("안녕하세요"))
                .andExpect(content().string(containsString("\"before_cursor\":null")))
                .andExpect(content().string(containsString("\"after_cursor\":null")));
    }

    @Test
    @DisplayName("발신자 실명이 있으면 name 으로 내리고 nickname 키는 내리지 않는다")
    void senderWithRealName() throws Exception {
        MessageItem item = new MessageItem(
                900L, MessageType.TEXT,
                new MessageItem.Sender(7L, "김홍엽", null, null),
                null, null, "안녕하세요", LocalDateTime.of(2026, 9, 5, 9, 0));
        given(chatMessageService.findMessages(42L, 30L, null, null, null))
                .willReturn(new MessageListResponse(List.of(item), null, null));

        mockMvc.perform(get("/api/chat-rooms/30/messages").with(member()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[0].sender.name").value("김홍엽"))
                .andExpect(jsonPath("$.data.items[0].sender.nickname").doesNotExist())
                .andExpect(jsonPath("$.data.items[0].sender.profile_image_url").isEmpty());
    }

    @Test
    @DisplayName("direction=before와 두 커서를 그대로 서비스에 전달하고, 응답에 두 커서가 모두 내려간다")
    void listBefore() throws Exception {
        given(chatMessageService.findMessages(42L, 30L, "before", "v1.b", "v1.a"))
                .willReturn(new MessageListResponse(List.of(), "v1.newBefore", "v1.a"));

        mockMvc.perform(get("/api/chat-rooms/30/messages")
                        .queryParam("direction", "before")
                        .queryParam("before", "v1.b")
                        .queryParam("after", "v1.a")
                        .with(member()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.before_cursor").value("v1.newBefore"))
                .andExpect(jsonPath("$.data.after_cursor").value("v1.a"));

        verify(chatMessageService).findMessages(42L, 30L, "before", "v1.b", "v1.a");
    }

    @Test
    @DisplayName("direction=after와 두 커서를 그대로 서비스에 전달하고, 끝에 도달하면 after_cursor가 null로 내려간다")
    void listAfter() throws Exception {
        given(chatMessageService.findMessages(42L, 30L, "after", "v1.b", "v1.a"))
                .willReturn(new MessageListResponse(List.of(), "v1.b", null));

        mockMvc.perform(get("/api/chat-rooms/30/messages")
                        .queryParam("direction", "after")
                        .queryParam("before", "v1.b")
                        .queryParam("after", "v1.a")
                        .with(member()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.before_cursor").value("v1.b"))
                .andExpect(content().string(containsString("\"after_cursor\":null")));
    }

    @Test
    @DisplayName("잘못된 커서면 400 INVALID_CURSOR다")
    void invalidCursor() throws Exception {
        given(chatMessageService.findMessages(42L, 30L, "before", "abc", null))
                .willThrow(new BusinessException(CommonErrorCode.INVALID_CURSOR));

        mockMvc.perform(get("/api/chat-rooms/30/messages")
                        .queryParam("direction", "before").queryParam("before", "abc").with(member()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("INVALID_CURSOR"));
    }

    @Test
    @DisplayName("잘못된 direction이면 400 INVALID_DIRECTION이다")
    void invalidDirection() throws Exception {
        given(chatMessageService.findMessages(42L, 30L, "up", null, null))
                .willThrow(new BusinessException(ChatErrorCode.INVALID_DIRECTION));

        mockMvc.perform(get("/api/chat-rooms/30/messages").queryParam("direction", "up").with(member()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("INVALID_DIRECTION"));
    }

    @Test
    @DisplayName("참여 중인 채팅방이 아니면 404 CHATROOM_NOT_FOUND다")
    void notParticipating() throws Exception {
        given(chatMessageService.findMessages(42L, 30L, null, null, null))
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
