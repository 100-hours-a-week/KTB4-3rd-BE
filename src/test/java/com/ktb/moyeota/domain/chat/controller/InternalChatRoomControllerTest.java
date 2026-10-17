package com.ktb.moyeota.domain.chat.controller;

import static org.mockito.BDDMockito.given;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ktb.moyeota.domain.chat.dto.ChatMessageSendRequest;
import com.ktb.moyeota.domain.chat.dto.MessageItem;
import com.ktb.moyeota.domain.chat.entity.MessageType;
import com.ktb.moyeota.domain.chat.service.ChatMessageSendService;
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
import com.ktb.moyeota.global.websocket.relay.RecordingWsRelayPublisher;
import java.time.LocalDateTime;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(controllers = InternalChatRoomController.class)
@Import({SecurityConfig.class, CorsConfig.class, JwtConfig.class, ClockConfig.class, WebConfig.class,
        AuthUserArgumentResolver.class, SignupPrincipalArgumentResolver.class, UploadScopeArgumentResolver.class,
        ApiAuthenticationEntryPoint.class, ApiAccessDeniedHandler.class, GlobalExceptionHandler.class,
        AccessTokenProvider.class, RecordingWsRelayPublisher.class})
@EnableConfigurationProperties({AuthProperties.class, CorsProperties.class})
class InternalChatRoomControllerTest {

    private static final String PATH = "/internal/chat-rooms/6/participants/4";
    private static final String KEY = "test-only-internal-api-key";
    private static final String MESSAGES = "/internal/chat-rooms/6/messages";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private AccessTokenProvider accessTokenProvider;

    @MockitoBean
    private ChatRoomService chatRoomService;

    @MockitoBean
    private ChatMessageSendService chatMessageSendService;

    @Autowired
    private RecordingWsRelayPublisher publisher;

    @MockitoBean
    private SignupSessionAuthenticator signupSessionAuthenticator;

    @Test
    @DisplayName("내부 키가 맞고 참여 중이면 is_participant true")
    void participant() throws Exception {
        given(chatRoomService.isActiveParticipant(6L, 4L)).willReturn(true);

        mockMvc.perform(get(PATH).header(InternalApiKeyFilter.HEADER, KEY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("채팅방 참여 여부를 확인했습니다"))
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

    @BeforeEach
    void clearPublished() {
        publisher.clear();
    }

    @Test
    @DisplayName("채팅을 저장하면 저장된 메시지를 돌려주고 그 채팅방으로 발행한다")
    void sendsMessage() throws Exception {
        MessageItem saved = new MessageItem(900L, MessageType.TEXT, new MessageItem.Sender(4L, "김준호", null, null),
                null, null, "출발지 앞이에요", LocalDateTime.of(2026, 10, 17, 9, 0));
        given(chatMessageSendService.send(4L, 6L, new ChatMessageSendRequest("c-1", "출발지 앞이에요")))
                .willReturn(Optional.of(saved));

        mockMvc.perform(post(MESSAGES).header(InternalApiKeyFilter.HEADER, KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"user_id\":4,\"client_message_id\":\"c-1\",\"content\":\"출발지 앞이에요\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("채팅 메시지를 저장했습니다"))
                .andExpect(jsonPath("$.data.id").value(900))
                .andExpect(jsonPath("$.data.content").value("출발지 앞이에요"));

        assertThat(publisher.published())
                .containsExactly(new RecordingWsRelayPublisher.Published("room", 6L, "/sub/chat/6", saved));
    }

    @Test
    @DisplayName("참여자가 아니면 방이 있는지 드러내지 않고 404, 발행하지 않는다")
    void sendByNonParticipant() throws Exception {
        given(chatMessageSendService.send(4L, 6L, new ChatMessageSendRequest("c-1", "출발지 앞이에요")))
                .willReturn(Optional.empty());

        mockMvc.perform(post(MESSAGES).header(InternalApiKeyFilter.HEADER, KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"user_id\":4,\"client_message_id\":\"c-1\",\"content\":\"출발지 앞이에요\"}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("CHATROOM_NOT_FOUND"));

        assertThat(publisher.published()).isEmpty();
    }

    @Test
    @DisplayName("내용이 비어 있으면 422, 발행하지 않는다")
    void blankContent() throws Exception {
        mockMvc.perform(post(MESSAGES).header(InternalApiKeyFilter.HEADER, KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"user_id\":4,\"client_message_id\":\"c-1\",\"content\":\" \"}"))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.error.field").value("content"));

        assertThat(publisher.published()).isEmpty();
    }

    @Test
    @DisplayName("보낸 사람이 없으면 422")
    void missingUser() throws Exception {
        mockMvc.perform(post(MESSAGES).header(InternalApiKeyFilter.HEADER, KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"client_message_id\":\"c-1\",\"content\":\"출발지 앞이에요\"}"))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.error.field").value("user_id"));
    }

    @Test
    @DisplayName("내부 키가 없으면 401, 저장도 발행도 하지 않는다")
    void sendWithoutKey() throws Exception {
        mockMvc.perform(post(MESSAGES)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"user_id\":4,\"client_message_id\":\"c-1\",\"content\":\"출발지 앞이에요\"}"))
                .andExpect(status().isUnauthorized());

        assertThat(publisher.published()).isEmpty();
    }
}
