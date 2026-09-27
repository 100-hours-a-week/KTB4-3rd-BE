package com.ktb.moyeota.domain.user.controller;

import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ktb.moyeota.domain.user.model.MyProfile;
import com.ktb.moyeota.global.exception.BusinessException;
import com.ktb.moyeota.global.exception.CommonErrorCode;
import com.ktb.moyeota.global.security.Authority;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

class MyProfileApiTest extends SignupApiTestSupport {

    private static final String MY_PROFILE = "/api/users/me";

    @Test
    @DisplayName("토큰의 사용자 정보를 내린다")
    void me() throws Exception {
        given(userService.findMe(42L))
                .willReturn(new MyProfile(42L, "길동이", "https://cdn.moyeota.test/profile/a.jpg", true));

        mockMvc.perform(get(MY_PROFILE).with(member()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("내 정보 조회에 성공했습니다"))
                .andExpect(jsonPath("$.data.id").value(42))
                .andExpect(jsonPath("$.data.nickname").value("길동이"))
                .andExpect(jsonPath("$.data.profile_image_url").value("https://cdn.moyeota.test/profile/a.jpg"))
                .andExpect(jsonPath("$.data.has_bank_account").value(true));
    }

    @Test
    @DisplayName("프로필 이미지가 없으면 profile_image_url 은 null 로 내린다")
    void withoutProfileImage() throws Exception {
        given(userService.findMe(42L)).willReturn(new MyProfile(42L, "길동이", null, false));

        mockMvc.perform(get(MY_PROFILE).with(member()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.profile_image_url").isEmpty())
                .andExpect(jsonPath("$.data.has_bank_account").value(false));
    }

    @Test
    @DisplayName("액세스 토큰이 없으면 401이다")
    void anonymous() throws Exception {
        mockMvc.perform(get(MY_PROFILE))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("UNAUTHORIZED"));

        verifyNoInteractions(userService);
    }

    @Test
    @DisplayName("회원가입 쿠키로는 호출할 수 없다")
    void signupSession() throws Exception {
        mockMvc.perform(get(MY_PROFILE).cookie(validSignupCookie()))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(userService);
    }

    @Test
    @DisplayName("토큰의 사용자가 없으면 401이다")
    void unknownUser() throws Exception {
        given(userService.findMe(42L)).willThrow(new BusinessException(CommonErrorCode.UNAUTHORIZED));

        mockMvc.perform(get(MY_PROFILE).with(member()))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("UNAUTHORIZED"));
    }

    private static RequestPostProcessor member() {
        return jwt().jwt(builder -> builder.subject("42")).authorities(Authority.USER);
    }
}
