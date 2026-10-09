package com.ktb.moyeota.domain.notification.controller;

import static com.ktb.moyeota.fixture.CompanionFixture.companionPost;
import static com.ktb.moyeota.fixture.UserFixture.user;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ktb.moyeota.domain.chat.entity.ChatRoom;
import com.ktb.moyeota.domain.notification.entity.Notification;
import com.ktb.moyeota.domain.notification.entity.NotificationType;
import com.ktb.moyeota.domain.notification.repository.NotificationRepository;
import com.ktb.moyeota.domain.notification.service.NotificationService;
import com.ktb.moyeota.domain.user.entity.User;
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
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
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
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

/**
 * 검증 로직이 서비스에 있어서, 서비스는 실제 빈을 쓰고 Repository만 mock으로 둔다.
 */
@WebMvcTest(controllers = NotificationController.class)
@Import({NotificationService.class,
        SecurityConfig.class, CorsConfig.class, JwtConfig.class, ClockConfig.class, WebConfig.class,
        AuthUserArgumentResolver.class, SignupPrincipalArgumentResolver.class, UploadScopeArgumentResolver.class,
        ApiAuthenticationEntryPoint.class, ApiAccessDeniedHandler.class, GlobalExceptionHandler.class})
@EnableConfigurationProperties({AuthProperties.class, CorsProperties.class})
class NotificationControllerTest {

    private static final Long USER_ID = 42L;
    private static final String LIST_URL = "/api/notifications";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private NotificationRepository notificationRepository;

    @MockitoBean
    private SignupSessionAuthenticator signupSessionAuthenticator;

    @Nested
    @DisplayName("GET /api/notifications - 유효성 검사")
    class ListValidation {

        @Test
        @DisplayName("tab=read면 422 VALIDATION_ERROR(field: tab)이고 조회하지 않는다")
        void readTab() throws Exception {
            mockMvc.perform(get(LIST_URL).param("tab", "read").with(member()))
                    .andExpect(status().isUnprocessableEntity())
                    .andExpect(jsonPath("$.message").value("지원하지 않는 탭이에요"))
                    .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"))
                    .andExpect(jsonPath("$.error.field").value("tab"));

            verifyNoInteractions(notificationRepository);
        }

        @ParameterizedTest
        @ValueSource(strings = {"5", "", "ALL", "abc"})
        @DisplayName("tab이 all, unread, read가 아니면 400 VALIDATION_ERROR(field: tab)이고 조회하지 않는다")
        void invalidTab(String tab) throws Exception {
            mockMvc.perform(get(LIST_URL).param("tab", tab).with(member()))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.message").value("탭 값이 올바르지 않아요"))
                    .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"))
                    .andExpect(jsonPath("$.error.field").value("tab"));

            verifyNoInteractions(notificationRepository);
        }

        @ParameterizedTest
        @ValueSource(strings = {"abc", "0", "-1", "99999999999999999999"})
        @DisplayName("cursor가 1 이상의 Long이 아니면 400 VALIDATION_ERROR(field: cursor)이고 조회하지 않는다")
        void invalidCursor(String cursor) throws Exception {
            mockMvc.perform(get(LIST_URL).param("cursor", cursor).with(member()))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.message").value("커서 값이 올바르지 않아요"))
                    .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"))
                    .andExpect(jsonPath("$.error.field").value("cursor"));

            verifyNoInteractions(notificationRepository);
        }

        @Test
        @DisplayName("액세스 토큰이 없으면 401이고 조회하지 않는다")
        void anonymous() throws Exception {
            mockMvc.perform(get(LIST_URL))
                    .andExpect(status().isUnauthorized());

            verifyNoInteractions(notificationRepository);
        }
    }

    @Nested
    @DisplayName("GET /api/notifications - 성공")
    class ListSuccess {

        @Test
        @DisplayName("tab이 없으면 전체 조회로 200과 snake_case 응답을 내려준다")
        void found() throws Exception {
            given(notificationRepository.findByRecipientWithCursor(eq(USER_ID), eq(false), isNull(), any()))
                    .willReturn(List.of(matchingNotification(899L)));

            mockMvc.perform(get(LIST_URL).with(member()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.message").value("조회에 성공했습니다"))
                    .andExpect(jsonPath("$.data.notifications[0].id").value(899))
                    .andExpect(jsonPath("$.data.notifications[0].type").value("MATCHING_COMPLETED"))
                    .andExpect(jsonPath("$.data.notifications[0].event_id").value(40))
                    .andExpect(jsonPath("$.data.notifications[0].target_chat_room_id").value(54))
                    .andExpect(jsonPath("$.data.notifications[0].target_post_id").value(nullValue()))
                    .andExpect(jsonPath("$.data.notifications[0].content").value("매칭이 완료됐어요"))
                    .andExpect(jsonPath("$.data.notifications[0].read_at").value(nullValue()))
                    .andExpect(jsonPath("$.data.next_cursor").value(nullValue()));
        }

        @Test
        @DisplayName("tab=unread, cursor가 있으면 안읽은 알림을 cursor 이후로 조회한다")
        void unreadWithCursor() throws Exception {
            given(notificationRepository.findByRecipientWithCursor(eq(USER_ID), eq(true), eq(881L), any()))
                    .willReturn(List.of(matchingNotification(880L)));

            mockMvc.perform(get(LIST_URL).param("tab", "unread").param("cursor", "881").with(member()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.notifications[0].id").value(880));
        }

        @Test
        @DisplayName("알림이 없으면 200 '알림 내역이 없어요'와 빈 목록을 내려준다")
        void empty() throws Exception {
            given(notificationRepository.findByRecipientWithCursor(eq(USER_ID), eq(false), isNull(), any()))
                    .willReturn(List.of());

            mockMvc.perform(get(LIST_URL).param("tab", "all").with(member()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.message").value("알림 내역이 없어요"))
                    .andExpect(jsonPath("$.data.notifications").isEmpty())
                    .andExpect(jsonPath("$.data.next_cursor").value(nullValue()));
        }
    }

    @Nested
    @DisplayName("PATCH /api/notifications/{notification_id}/read_at")
    class MarkRead {

        @Test
        @DisplayName("안읽은 내 알림이면 200과 read_at을 내려준다")
        void success() throws Exception {
            given(notificationRepository.markReadIfUnread(eq(900L), eq(USER_ID), any())).willReturn(1);

            mockMvc.perform(patch("/api/notifications/900/read_at").with(member()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.message").value("읽음 처리되었습니다"))
                    .andExpect(jsonPath("$.data.read_at").exists());
        }

        @Test
        @DisplayName("이미 읽은 알림이면 200과 기존 read_at을 내려준다")
        void alreadyRead() throws Exception {
            given(notificationRepository.markReadIfUnread(eq(900L), eq(USER_ID), any())).willReturn(0);
            given(notificationRepository.findReadAtByIdAndRecipientId(900L, USER_ID))
                    .willReturn(Optional.of(LocalDateTime.of(2026, 9, 6, 9, 10, 0, 123_456_000)));

            mockMvc.perform(patch("/api/notifications/900/read_at").with(member()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.read_at").value("2026-09-06T09:10:00.123456"));
        }

        @Test
        @DisplayName("내 알림이 아니거나 없으면 404 NOT_FOUND다")
        void notFound() throws Exception {
            given(notificationRepository.markReadIfUnread(eq(900L), eq(USER_ID), any())).willReturn(0);
            given(notificationRepository.findReadAtByIdAndRecipientId(900L, USER_ID)).willReturn(Optional.empty());

            mockMvc.perform(patch("/api/notifications/900/read_at").with(member()))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.message").value("알림을 찾을 수 없어요"))
                    .andExpect(jsonPath("$.error.code").value("NOT_FOUND"));
        }

        @Test
        @DisplayName("notification_id가 숫자가 아니면 400이고 조회하지 않는다")
        void invalidId() throws Exception {
            mockMvc.perform(patch("/api/notifications/abc/read_at").with(member()))
                    .andExpect(status().isBadRequest());

            verifyNoInteractions(notificationRepository);
        }

        @Test
        @DisplayName("액세스 토큰이 없으면 401이고 조회하지 않는다")
        void anonymous() throws Exception {
            mockMvc.perform(patch("/api/notifications/900/read_at"))
                    .andExpect(status().isUnauthorized());

            verifyNoInteractions(notificationRepository);
        }
    }

    private static Notification matchingNotification(Long id) {
        User recipient = user(USER_ID, "수신자");
        ChatRoom chatRoom = ChatRoom.create(companionPost(recipient));
        ReflectionTestUtils.setField(chatRoom, "id", 54L);

        Notification notification = Notification.createForChatRoom(
                recipient, NotificationType.MATCHING_COMPLETED, 40L, chatRoom, "매칭이 완료됐어요");
        ReflectionTestUtils.setField(notification, "id", id);
        ReflectionTestUtils.setField(notification, "createdAt", LocalDateTime.of(2026, 9, 4, 9, 0));
        return notification;
    }

    private static RequestPostProcessor member() {
        return jwt().jwt(builder -> builder.subject(String.valueOf(USER_ID))).authorities(Authority.USER);
    }
}
