package com.ktb.moyeota.domain.settlement.controller;

import static org.hamcrest.Matchers.hasKey;
import static org.hamcrest.Matchers.not;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ktb.moyeota.domain.settlement.entity.MeterAnalysisStatus;
import com.ktb.moyeota.domain.settlement.entity.SettlementStatus;
import com.ktb.moyeota.domain.settlement.error.SettlementErrorCode;
import com.ktb.moyeota.domain.settlement.model.MeterAnalysis;
import com.ktb.moyeota.domain.settlement.model.RequestedSettlement;
import com.ktb.moyeota.domain.settlement.service.SettlementService;
import com.ktb.moyeota.domain.taxipot.error.TaxiPotErrorCode;
import com.ktb.moyeota.global.config.ClockConfig;
import com.ktb.moyeota.global.config.CorsConfig;
import com.ktb.moyeota.global.config.CorsProperties;
import com.ktb.moyeota.global.config.WebConfig;
import com.ktb.moyeota.global.exception.BusinessException;
import com.ktb.moyeota.global.exception.ErrorCode;
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
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

@WebMvcTest(controllers = SettlementController.class)
@Import({SecurityConfig.class, CorsConfig.class, JwtConfig.class, ClockConfig.class, WebConfig.class,
        AuthUserArgumentResolver.class, SignupPrincipalArgumentResolver.class, UploadScopeArgumentResolver.class,
        ApiAuthenticationEntryPoint.class, ApiAccessDeniedHandler.class, GlobalExceptionHandler.class})
@EnableConfigurationProperties({AuthProperties.class, CorsProperties.class})
class SettlementControllerTest {

    private static final String ANALYSIS_STATUS = "/api/settlements/77/analysis-status";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private SettlementService settlementService;

    @MockitoBean
    private SignupSessionAuthenticator signupSessionAuthenticator;

    @Test
    @DisplayName("금액을 입력하면 200과 인원 · 인당 금액 · 바뀐 상태를 내린다")
    void inputAmount() throws Exception {
        given(settlementService.requestManually(42L, 77L, 18000)).willReturn(
                new RequestedSettlement(3, 6000, SettlementStatus.REQUESTED, MeterAnalysisStatus.MANUAL));

        mockMvc.perform(inputAmount("{\"total_amount\":18000}").with(member()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("수동으로 입력받은 값으로 정산을 진행합니다."))
                .andExpect(jsonPath("$.data.participant_count").value(3))
                .andExpect(jsonPath("$.data.amount_per_person").value(6000))
                .andExpect(jsonPath("$.data.group_settlement_status").value("REQUESTED"))
                .andExpect(jsonPath("$.data.meter_analysis_status").value("MANUAL"));
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {"{}", "{\"total_amount\":null}", "{\"total_amount\":0}", "{\"total_amount\":-1000}",
            "{\"total_amount\":1000000}", "{\"total_amount\":18000.5}"})
    @DisplayName("금액이 없거나 1원 ~ 999,999원의 정수가 아니면 422 VALIDATION_ERROR다")
    void invalidAmount(String body) throws Exception {
        mockMvc.perform(inputAmount(body).with(member()))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.error.field").value("total_amount"));

        verifyNoInteractions(settlementService);
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {"{\"total_amount\":\"18,000\"}", "{\"total_amount\":\"abc\"}"})
    @DisplayName("금액이 숫자가 아니면 400 MALFORMED_REQUEST다")
    void notNumber(String body) throws Exception {
        mockMvc.perform(inputAmount(body).with(member()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("MALFORMED_REQUEST"));

        verifyNoInteractions(settlementService);
    }

    @ParameterizedTest(name = "{1}")
    @MethodSource("failures")
    @DisplayName("서비스가 거절하면 그 상태와 코드를 내린다")
    void rejected(ErrorCode errorCode, String expectedCode, int expectedStatus) throws Exception {
        given(settlementService.requestManually(42L, 77L, 18000)).willThrow(new BusinessException(errorCode));

        mockMvc.perform(inputAmount("{\"total_amount\":18000}").with(member()))
                .andExpect(status().is(expectedStatus))
                .andExpect(jsonPath("$.error.code").value(expectedCode));
    }

    static Stream<Arguments> failures() {
        return Stream.of(
                Arguments.of(SettlementErrorCode.SETTLEMENT_NOT_FOUND, "SETTLEMENT_NOT_FOUND", 404),
                Arguments.of(TaxiPotErrorCode.HOST_ONLY, "HOST_ONLY", 403),
                Arguments.of(SettlementErrorCode.ALREADY_REQUESTED, "ALREADY_REQUESTED", 409));
    }

    @Test
    @DisplayName("로그인하지 않으면 401이다")
    void unauthenticated() throws Exception {
        mockMvc.perform(inputAmount("{\"total_amount\":18000}"))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(settlementService);
    }

    @Test
    @DisplayName("미터기 분석 중이면 200과 정산 상태 · 분석 상태 · 분석 시작 시각을 내린다")
    void meterAnalyzing() throws Exception {
        given(settlementService.findMeterAnalysis(42L, 77L)).willReturn(new MeterAnalysis(SettlementStatus.PENDING,
                MeterAnalysisStatus.ANALYZING, null, LocalDateTime.of(2026, 9, 7, 9, 0, 3)));

        mockMvc.perform(get(ANALYSIS_STATUS).with(member()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("미터기 금액 인식 상태를 조회했습니다"))
                .andExpect(jsonPath("$.data.group_settlement_status").value("PENDING"))
                .andExpect(jsonPath("$.data.meter_analysis_status").value("ANALYZING"))
                .andExpect(jsonPath("$.data", hasKey("total_amount")))
                .andExpect(jsonPath("$.data.total_amount").isEmpty())
                .andExpect(jsonPath("$.data.analysis_started_at").value("2026-09-07T09:00:03"));
    }

    @Test
    @DisplayName("금액이 정해지면 총액을 내린다")
    void meterAmountDecided() throws Exception {
        given(settlementService.findMeterAnalysis(42L, 77L)).willReturn(new MeterAnalysis(SettlementStatus.REQUESTED,
                MeterAnalysisStatus.MANUAL, 18000, null));

        mockMvc.perform(get(ANALYSIS_STATUS).with(member()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.group_settlement_status").value("REQUESTED"))
                .andExpect(jsonPath("$.data.meter_analysis_status").value("MANUAL"))
                .andExpect(jsonPath("$.data.total_amount").value(18000));
    }

    @Test
    @DisplayName("미터기 사진을 내기 전이면 분석 시작 시각 키를 내리지 않는다")
    void meterNotSubmitted() throws Exception {
        given(settlementService.findMeterAnalysis(42L, 77L)).willReturn(new MeterAnalysis(SettlementStatus.PENDING,
                MeterAnalysisStatus.PENDING, null, null));

        mockMvc.perform(get(ANALYSIS_STATUS).with(member()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.meter_analysis_status").value("PENDING"))
                .andExpect(jsonPath("$.data", not(hasKey("analysis_started_at"))));
    }

    @Test
    @DisplayName("볼 수 없는 정산이면 404 SETTLEMENT_NOT_FOUND다")
    void meterAnalysisNotFound() throws Exception {
        given(settlementService.findMeterAnalysis(42L, 77L))
                .willThrow(new BusinessException(SettlementErrorCode.SETTLEMENT_NOT_FOUND));

        mockMvc.perform(get(ANALYSIS_STATUS).with(member()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("SETTLEMENT_NOT_FOUND"));
    }

    private static MockHttpServletRequestBuilder inputAmount(String body) {
        return put("/api/settlements/77/amount").contentType(MediaType.APPLICATION_JSON).content(body);
    }

    private static RequestPostProcessor member() {
        return jwt().jwt(builder -> builder.subject("42")).authorities(Authority.USER);
    }
}
