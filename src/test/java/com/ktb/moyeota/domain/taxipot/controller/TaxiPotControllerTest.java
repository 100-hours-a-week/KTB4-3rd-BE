package com.ktb.moyeota.domain.taxipot.controller;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ktb.moyeota.domain.companion.entity.CompanionStatus;
import com.ktb.moyeota.domain.taxipot.error.TaxiPotErrorCode;
import com.ktb.moyeota.domain.taxipot.model.CurrentTaxiPot;
import com.ktb.moyeota.domain.taxipot.model.TaxiPotDetail;
import com.ktb.moyeota.domain.taxipot.service.TaxiPotService;
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
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

@WebMvcTest(controllers = TaxiPotController.class)
@Import({SecurityConfig.class, CorsConfig.class, JwtConfig.class, ClockConfig.class, WebConfig.class,
        AuthUserArgumentResolver.class, SignupPrincipalArgumentResolver.class, UploadScopeArgumentResolver.class,
        ApiAuthenticationEntryPoint.class, ApiAccessDeniedHandler.class, GlobalExceptionHandler.class})
@EnableConfigurationProperties({AuthProperties.class, CorsProperties.class})
class TaxiPotControllerTest {

    private static final String CURRENT_TAXI_POT = "/api/users/me/current-taxi-pot";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private TaxiPotService taxiPotService;

    @MockitoBean
    private SignupSessionAuthenticator signupSessionAuthenticator;

    @Test
    @DisplayName("진행 중인 택시팟이 있으면 요약을 내린다")
    void current() throws Exception {
        given(taxiPotService.findMyCurrent(42L))
                .willReturn(Optional.of(new CurrentTaxiPot(30L, CompanionStatus.RECRUITING, 2, 4)));

        mockMvc.perform(get(CURRENT_TAXI_POT).with(member()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("조회에 성공했습니다"))
                .andExpect(jsonPath("$.data.id").value(30))
                .andExpect(jsonPath("$.data.status").value("RECRUITING"))
                .andExpect(jsonPath("$.data.current_count").value(2))
                .andExpect(jsonPath("$.data.capacity").value(4))
                .andExpect(content().string(containsString("\"chat_room_id\":null")));
    }

    @Test
    @DisplayName("진행 중인 택시팟이 없어도 404가 아니라 200과 data null이다")
    void none() throws Exception {
        given(taxiPotService.findMyCurrent(42L)).willReturn(Optional.empty());

        mockMvc.perform(get(CURRENT_TAXI_POT).with(member()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("진행 중인 매칭이 없습니다"))
                .andExpect(content().string(containsString("\"data\":null")))
                .andExpect(jsonPath("$.error").doesNotExist());
    }

    @Test
    @DisplayName("액세스 토큰이 없으면 401이다")
    void anonymous() throws Exception {
        mockMvc.perform(get(CURRENT_TAXI_POT))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("UNAUTHORIZED"));

        verifyNoInteractions(taxiPotService);
    }

    @Test
    @DisplayName("참여 중인 택시팟이면 200과 상세 정보를 내린다")
    void detail() throws Exception {
        given(taxiPotService.find(42L, 30L)).willReturn(new TaxiPotDetail(30L, CompanionStatus.IN_PROGRESS,
                "판교역", "강남역", LocalDateTime.of(2026, 9, 5, 17, 30), 3, 4, 7L));

        mockMvc.perform(get("/api/taxi-pots/30").with(member()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("조회에 성공했습니다"))
                .andExpect(jsonPath("$.data.id").value(30))
                .andExpect(jsonPath("$.data.status").value("IN_PROGRESS"))
                .andExpect(jsonPath("$.data.origin_name").value("판교역"))
                .andExpect(jsonPath("$.data.dest_name").value("강남역"))
                .andExpect(jsonPath("$.data.departure_at").value("2026-09-05T17:30:00"))
                .andExpect(jsonPath("$.data.current_count").value(3))
                .andExpect(jsonPath("$.data.capacity").value(4))
                .andExpect(jsonPath("$.data.host_id").value(7))
                .andExpect(content().string(containsString("\"chat_room_id\":null")));
    }

    @Test
    @DisplayName("참여하지 않은 택시팟이면 404 TAXI_POT_NOT_FOUND다")
    void detailNotFound() throws Exception {
        given(taxiPotService.find(42L, 30L)).willThrow(new BusinessException(TaxiPotErrorCode.TAXI_POT_NOT_FOUND));

        mockMvc.perform(get("/api/taxi-pots/30").with(member()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("TAXI_POT_NOT_FOUND"))
                .andExpect(jsonPath("$.message").value("존재하지 않는 매칭입니다"));
    }

    private static RequestPostProcessor member() {
        return jwt().jwt(builder -> builder.subject("42")).authorities(Authority.USER);
    }
}
