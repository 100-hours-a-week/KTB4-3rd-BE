package com.ktb.moyeota.domain.user.controller;

import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ktb.moyeota.global.security.Authority;
import jakarta.servlet.http.Cookie;
import java.time.LocalDateTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

class NicknameAvailabilityApiTest extends SignupApiTestSupport {

    @Nested
    @DisplayName("호출 자격")
    class Credentials {

        @Test
        @DisplayName("회원가입 쿠키로 호출할 수 있다")
        void signupSession() throws Exception {
            given(userService.isNicknameAvailable("길동이")).willReturn(true);

            mockMvc.perform(checkNickname("길동이").cookie(validSignupCookie()))
                    .andExpect(status().isOk());
        }

        @Test
        @DisplayName("회원의 액세스 토큰으로도 호출할 수 있다")
        void memberAccessToken() throws Exception {
            given(userService.isNicknameAvailable("길동이")).willReturn(true);

            mockMvc.perform(checkNickname("길동이").with(member()))
                    .andExpect(status().isOk());
        }

        @Test
        @DisplayName("아무 자격도 없으면 401이다")
        void anonymous() throws Exception {
            mockMvc.perform(checkNickname("길동이"))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.error.code").value("UNAUTHORIZED"));

            verifyNoInteractions(userService);
        }

        @Test
        @DisplayName("발급한 적 없는 회원가입 쿠키면 401이다")
        void unknownCookie() throws Exception {
            mockMvc.perform(checkNickname("길동이").cookie(new Cookie(SIGNUP_TOKEN, "never-issued")))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("15분이 지난 회원가입 세션이면 401이다")
        void expiredSession() throws Exception {
            mockMvc.perform(checkNickname("길동이")
                            .cookie(signupCookie(LocalDateTime.now().minusSeconds(1))))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("회원가입 쿠키가 낡았어도 액세스 토큰이 유효하면 호출할 수 있다")
        void staleCookieWithAccessToken() throws Exception {
            given(userService.isNicknameAvailable("길동이")).willReturn(true);

            mockMvc.perform(checkNickname("길동이")
                            .cookie(new Cookie(SIGNUP_TOKEN, "never-issued"))
                            .with(member()))
                    .andExpect(status().isOk());
        }
    }

    @Nested
    @DisplayName("응답")
    class Response {

        @Test
        @DisplayName("쓸 수 있는 닉네임이면 available true다")
        void available() throws Exception {
            given(userService.isNicknameAvailable("길동이")).willReturn(true);

            mockMvc.perform(checkNickname("길동이").cookie(validSignupCookie()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.message").value("사용할 수 있는 닉네임이에요"))
                    .andExpect(jsonPath("$.data.available").value(true));
        }

        @Test
        @DisplayName("이미 쓰이는 닉네임이어도 오류가 아니라 200과 available false다")
        void taken() throws Exception {
            given(userService.isNicknameAvailable("길동이")).willReturn(false);

            mockMvc.perform(checkNickname("길동이").cookie(validSignupCookie()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.message").value("이미 사용 중인 닉네임이에요"))
                    .andExpect(jsonPath("$.data.available").value(false))
                    .andExpect(jsonPath("$.error").doesNotExist());
        }

        @Test
        @DisplayName("길이 경계인 2자와 12자는 통과한다")
        void lengthBoundaries() throws Exception {
            mockMvc.perform(checkNickname("길동").cookie(validSignupCookie()))
                    .andExpect(status().isOk());
            mockMvc.perform(checkNickname("abcdefghij12").cookie(validSignupCookie()))
                    .andExpect(status().isOk());

            verify(userService).isNicknameAvailable("길동");
            verify(userService).isNicknameAvailable("abcdefghij12");
        }
    }

    @Nested
    @DisplayName("닉네임 형식 검증")
    class Validation {

        @Test
        @DisplayName("1자면 LENGTH_OUT_OF_RANGE이고 조회하지 않는다")
        void tooShort() throws Exception {
            mockMvc.perform(checkNickname("길").cookie(validSignupCookie()))
                    .andExpect(status().isUnprocessableContent())
                    .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"))
                    .andExpect(jsonPath("$.error.field").value("nickname"))
                    .andExpect(jsonPath("$.error.details[0].reason").value("LENGTH_OUT_OF_RANGE"));

            verifyNoInteractions(userService);
        }

        @Test
        @DisplayName("13자면 LENGTH_OUT_OF_RANGE다")
        void tooLong() throws Exception {
            mockMvc.perform(checkNickname("abcdefghij123").cookie(validSignupCookie()))
                    .andExpect(status().isUnprocessableContent())
                    .andExpect(jsonPath("$.error.details[0].reason").value("LENGTH_OUT_OF_RANGE"));
        }

        @Test
        @DisplayName("한글·영문·숫자가 아닌 문자가 있으면 INVALID_FORMAT이다")
        void invalidCharacter() throws Exception {
            mockMvc.perform(checkNickname("길동_이").cookie(validSignupCookie()))
                    .andExpect(status().isUnprocessableContent())
                    .andExpect(jsonPath("$.error.field").value("nickname"))
                    .andExpect(jsonPath("$.error.details[0].reason").value("INVALID_FORMAT"));

            verifyNoInteractions(userService);
        }

        @Test
        @DisplayName("nickname 파라미터가 없으면 REQUIRED다")
        void missingParameter() throws Exception {
            mockMvc.perform(get("/api/users/nickname-availability").cookie(validSignupCookie()))
                    .andExpect(status().isUnprocessableContent())
                    .andExpect(jsonPath("$.error.field").value("nickname"))
                    .andExpect(jsonPath("$.error.details[0].reason").value("REQUIRED"));
        }
    }

    private static MockHttpServletRequestBuilder checkNickname(String nickname) {
        return get("/api/users/nickname-availability").param("nickname", nickname);
    }

    private static RequestPostProcessor member() {
        return jwt().jwt(builder -> builder.subject("42")).authorities(Authority.USER);
    }
}
