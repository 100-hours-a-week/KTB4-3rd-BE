package com.ktb.moyeota.domain.chat.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.hamcrest.Matchers.containsString;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ktb.moyeota.domain.chat.dto.ChatRoomDetailResponse;
import com.ktb.moyeota.domain.chat.dto.ChatRoomItem;
import com.ktb.moyeota.domain.chat.dto.ChatRoomListResponse;
import com.ktb.moyeota.domain.chat.dto.ReadMarkerResponse;
import com.ktb.moyeota.domain.chat.exception.ChatErrorCode;
import com.ktb.moyeota.domain.chat.service.ChatRoomService;
import com.ktb.moyeota.domain.companion.entity.CompanionKind;
import com.ktb.moyeota.domain.companion.entity.CompanionStatus;
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
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

@WebMvcTest(controllers = ChatRoomController.class)
@Import({SecurityConfig.class, CorsConfig.class, JwtConfig.class, ClockConfig.class, WebConfig.class,
        AuthUserArgumentResolver.class, SignupPrincipalArgumentResolver.class, UploadScopeArgumentResolver.class,
        ApiAuthenticationEntryPoint.class, ApiAccessDeniedHandler.class, GlobalExceptionHandler.class})
@EnableConfigurationProperties({AuthProperties.class, CorsProperties.class})
class ChatRoomControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ChatRoomService chatRoomService;

    @MockitoBean
    private SignupSessionAuthenticator signupSessionAuthenticator;

    @Test
    @DisplayName("내 채팅방 목록을 조회한다")
    void list() throws Exception {
        ChatRoomItem item = new ChatRoomItem(
                1L, 10L, CompanionKind.TAXI_POT, "8시 판교역",
                new ChatRoomItem.Host("https://img"), 2, 4, true);
        given(chatRoomService.findMyChatRooms(42L, null, null))
                .willReturn(new ChatRoomListResponse(List.of(item), null));

        mockMvc.perform(get("/api/chat-rooms").with(member()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("조회에 성공했습니다"))
                .andExpect(jsonPath("$.data.items[0].id").value(1))
                .andExpect(jsonPath("$.data.items[0].companion_id").value(10))
                .andExpect(jsonPath("$.data.items[0].kind").value("TAXI_POT"))
                .andExpect(jsonPath("$.data.items[0].has_unread").value(true))
                .andExpect(content().string(containsString("\"next_cursor\":null")));
    }

    @Test
    @DisplayName("kind, cursor 쿼리 파라미터를 그대로 서비스에 전달한다")
    void listWithQueryParams() throws Exception {
        given(chatRoomService.findMyChatRooms(eq(42L), eq(CompanionKind.COMPANION), eq("v1.abc")))
                .willReturn(new ChatRoomListResponse(List.of(), null));

        mockMvc.perform(get("/api/chat-rooms")
                        .queryParam("kind", "COMPANION")
                        .queryParam("cursor", "v1.abc")
                        .with(member()))
                .andExpect(status().isOk());

        verify(chatRoomService).findMyChatRooms(42L, CompanionKind.COMPANION, "v1.abc");
    }

    @Test
    @DisplayName("액세스 토큰이 없으면 401이다")
    void listAnonymous() throws Exception {
        mockMvc.perform(get("/api/chat-rooms"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("UNAUTHORIZED"));

        verifyNoInteractions(chatRoomService);
    }

    @Test
    @DisplayName("채팅방 상세를 조회한다")
    void detail() throws Exception {
        ChatRoomDetailResponse response = new ChatRoomDetailResponse(
                30L, 10L, CompanionKind.TAXI_POT, "8시 판교역", 7L,
                "판교역", "강남역", LocalDateTime.of(2026, 9, 5, 8, 30),
                2, 4, CompanionStatus.RECRUITING, null, 99L);
        given(chatRoomService.findDetail(42L, 30L)).willReturn(response);

        mockMvc.perform(get("/api/chat-rooms/30").with(member()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(30))
                .andExpect(jsonPath("$.data.host_id").value(7))
                .andExpect(jsonPath("$.data.last_read_message_id").value(99));
    }

    @Test
    @DisplayName("존재하지 않는 채팅방이면 404 CHATROOM_NOT_FOUND다")
    void detailNotFound() throws Exception {
        given(chatRoomService.findDetail(42L, 30L))
                .willThrow(new BusinessException(ChatErrorCode.CHATROOM_NOT_FOUND));

        mockMvc.perform(get("/api/chat-rooms/30").with(member()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("CHATROOM_NOT_FOUND"));
    }

    @Test
    @DisplayName("읽음 처리에 성공하면 200과 마지막으로 읽은 메시지 id를 내린다")
    void markRead() throws Exception {
        given(chatRoomService.markRead(eq(42L), eq(30L), any())).willReturn(new ReadMarkerResponse(55L));

        mockMvc.perform(put("/api/chat-rooms/30/read-marker")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"last_read_message_id\":55}")
                        .with(member()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("읽음 처리에 성공했습니다"))
                .andExpect(jsonPath("$.data.last_read_message_id").value(55));
    }

    @Test
    @DisplayName("읽음 처리 요청 값이 없으면(null) 그대로 서비스에 전달한다")
    void markReadWithoutBody() throws Exception {
        given(chatRoomService.markRead(eq(42L), eq(30L), any())).willReturn(new ReadMarkerResponse(null));

        mockMvc.perform(put("/api/chat-rooms/30/read-marker")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}")
                        .with(member()))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("\"last_read_message_id\":null")));
    }

    private static RequestPostProcessor member() {
        return jwt().jwt(builder -> builder.subject("42")).authorities(Authority.USER);
    }
}
