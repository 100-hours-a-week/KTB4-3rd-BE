package com.ktb.moyeota.domain.user.controller;

import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ktb.moyeota.domain.user.error.UserErrorCode;
import com.ktb.moyeota.global.exception.BusinessException;
import com.ktb.moyeota.global.security.Authority;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

class WithdrawApiTest extends SignupApiTestSupport {

    @Test
    @DisplayName("탈퇴하면 204와 함께 리프레시 토큰 쿠키를 지운다")
    void withdraw() throws Exception {
        mockMvc.perform(delete("/api/users/me").with(member()))
                .andExpect(status().isNoContent())
                .andExpect(cookie().maxAge(REFRESH_TOKEN, 0))
                .andExpect(cookie().path(REFRESH_TOKEN, "/api/auth"));
    }

    @Test
    @DisplayName("진행 중인 동행의 방장이면 409 ACTIVE_HOST_EXISTS다")
    void activeHost() throws Exception {
        willThrow(new BusinessException(UserErrorCode.ACTIVE_HOST_EXISTS)).given(userService).withdraw(42L);

        mockMvc.perform(delete("/api/users/me").with(member()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("ACTIVE_HOST_EXISTS"));
    }

    @Test
    @DisplayName("참여 중인 택시팟이 있으면 409 ACTIVE_TAXI_POT_EXISTS다")
    void activeTaxiPot() throws Exception {
        willThrow(new BusinessException(UserErrorCode.ACTIVE_TAXI_POT_EXISTS)).given(userService).withdraw(42L);

        mockMvc.perform(delete("/api/users/me").with(member()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("ACTIVE_TAXI_POT_EXISTS"));
    }

    @Test
    @DisplayName("액세스 토큰이 없으면 401이다")
    void anonymous() throws Exception {
        mockMvc.perform(delete("/api/users/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("UNAUTHORIZED"));

        verifyNoInteractions(userService);
    }

    private static RequestPostProcessor member() {
        return jwt().jwt(builder -> builder.subject("42")).authorities(Authority.USER);
    }
}
