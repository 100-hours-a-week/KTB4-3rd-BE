package com.ktb.moyeota.domain.user.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ktb.moyeota.domain.auth.model.SignupSessionView;
import com.ktb.moyeota.domain.user.entity.Gender;
import com.ktb.moyeota.domain.user.model.AgreementsCommand;
import com.ktb.moyeota.domain.user.model.SignupCommand;
import com.ktb.moyeota.global.security.Authority;
import jakarta.servlet.http.Cookie;
import java.time.LocalDateTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpHeaders;

class UserControllerTest extends SignupApiTestSupport {

    @Nested
    @DisplayName("회원가입 세션 인증")
    class SignupSessionAuthentication {

        @Test
        @DisplayName("유효한 회원가입 쿠키면 가입할 수 있다")
        void validCookieCanSignUp() throws Exception {
            givenRegistered();

            mockMvc.perform(signUp(VALID_BODY).cookie(validSignupCookie()))
                    .andExpect(status().isCreated());
        }

        @Test
        @DisplayName("쿠키가 없으면 401이다")
        void noCookie() throws Exception {
            mockMvc.perform(signUp(VALID_BODY))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.error.code").value("UNAUTHORIZED"));

            verifyNoInteractions(userService);
        }

        @Test
        @DisplayName("발급한 적 없는 쿠키면 401이다")
        void unknownCookie() throws Exception {
            mockMvc.perform(signUp(VALID_BODY).cookie(new Cookie(SIGNUP_TOKEN, "never-issued")))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("15분이 지난 회원가입 세션이면 401이다")
        void expiredSession() throws Exception {
            mockMvc.perform(signUp(VALID_BODY).cookie(signupCookie(LocalDateTime.now().minusSeconds(1))))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.error.code").value("UNAUTHORIZED"));
        }

        @Test
        @DisplayName("이미 회원인 사용자의 액세스 토큰으로는 가입할 수 없다")
        void memberIsForbidden() throws Exception {
            mockMvc.perform(signUp(VALID_BODY)
                            .with(jwt().jwt(builder -> builder.subject("42")).authorities(Authority.USER)))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.error.code").value("FORBIDDEN"));
        }
    }

    @Nested
    @DisplayName("성공 응답")
    class Created {

        @Test
        @DisplayName("201과 Location, 액세스 토큰을 돌려준다")
        void respondsCreatedWithAccessToken() throws Exception {
            givenRegistered();

            mockMvc.perform(signUp(VALID_BODY).cookie(validSignupCookie()))
                    .andExpect(status().isCreated())
                    .andExpect(header().string(HttpHeaders.LOCATION, "/users/7"))
                    .andExpect(jsonPath("$.message").value("가입이 완료되었어요"))
                    .andExpect(jsonPath("$.data.user_id").value(7))
                    .andExpect(jsonPath("$.data.access_token").value("access-value"))
                    .andExpect(jsonPath("$.data.profile_image_url").value(nullValue()))
                    .andExpect(jsonPath("$.data.created_at").value("2026-09-06T09:00:00"));
        }

        @Test
        @DisplayName("리프레시 쿠키를 내려주고 회원가입 쿠키는 지운다")
        void swapsCookies() throws Exception {
            givenRegistered();

            mockMvc.perform(signUp(VALID_BODY).cookie(validSignupCookie()))
                    .andExpect(cookie().value(REFRESH_TOKEN, "refresh-value"))
                    .andExpect(cookie().maxAge(REFRESH_TOKEN, 604800))
                    .andExpect(cookie().path(REFRESH_TOKEN, "/auth"))
                    .andExpect(cookie().httpOnly(REFRESH_TOKEN, true))
                    .andExpect(cookie().maxAge(SIGNUP_TOKEN, 0))
                    .andExpect(cookie().path(SIGNUP_TOKEN, "/"));
        }

        @Test
        @DisplayName("쿠키가 가리키는 회원가입 세션과 본문을 서비스에 넘긴다")
        void passesSessionAndCommand() throws Exception {
            givenRegistered();
            Cookie cookie = validSignupCookie();

            mockMvc.perform(signUp(VALID_BODY).cookie(cookie));

            ArgumentCaptor<SignupSessionView> session = ArgumentCaptor.forClass(SignupSessionView.class);
            ArgumentCaptor<SignupCommand> command = ArgumentCaptor.forClass(SignupCommand.class);
            verify(userService).register(session.capture(), command.capture());
            assertThat(session.getValue().tokenHash()).isEqualTo(opaqueTokenFactory.hash(cookie.getValue()));
            assertThat(session.getValue().providerUserId()).isEqualTo("1234567890");
            assertThat(command.getValue()).isEqualTo(
                    new SignupCommand(
                            "길동이", Gender.FEMALE, null, null, new AgreementsCommand(true, false)));
        }
    }
}
