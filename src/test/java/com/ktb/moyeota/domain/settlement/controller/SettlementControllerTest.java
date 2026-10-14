package com.ktb.moyeota.domain.settlement.controller;

import static org.hamcrest.Matchers.hasKey;
import static org.hamcrest.Matchers.not;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ktb.moyeota.domain.image.error.ImageErrorCode;
import com.ktb.moyeota.domain.settlement.entity.MeterAnalysisStatus;
import com.ktb.moyeota.domain.settlement.entity.SettlementStatus;
import com.ktb.moyeota.domain.settlement.error.SettlementErrorCode;
import com.ktb.moyeota.domain.settlement.entity.TransferAnalysisStatus;
import com.ktb.moyeota.domain.settlement.model.MeterAnalysis;
import com.ktb.moyeota.domain.settlement.model.MeterImage;
import com.ktb.moyeota.domain.settlement.model.MyTransfer;
import com.ktb.moyeota.domain.settlement.model.RequestedSettlement;
import com.ktb.moyeota.domain.settlement.model.SettlementOverview;
import com.ktb.moyeota.domain.settlement.model.TransferProof;
import com.ktb.moyeota.domain.settlement.model.TransferSubmission;
import com.ktb.moyeota.domain.settlement.service.MeterImageService;
import com.ktb.moyeota.domain.settlement.service.SettlementOverviewService;
import com.ktb.moyeota.domain.settlement.service.SettlementService;
import com.ktb.moyeota.domain.settlement.service.TransferProofService;
import com.ktb.moyeota.domain.settlement.service.TransferProofSubmissionService;
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
import java.util.List;
import java.util.Optional;
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

    private static final String METER_IMAGE = "/api/settlements/77/meter-image";
    private static final String METER_KEY = "tmp/meter/u-42/2aac932d-f68b-4491-9891-fd47671fcf02.jpg";
    private static final String ANALYSIS_STATUS = "/api/settlements/77/analysis-status";
    private static final String MY_TRANSFER = "/api/settlements/77/participants/me";
    private static final String COMPLETION = "/api/settlements/77/completion";
    private static final String SETTLEMENT = "/api/settlements/77";
    private static final String TRANSFER_PROOF = "/api/settlements/77/participants/43/transfer-proof";
    private static final String TRANSFER_SUBMIT = "/api/settlements/77/transfer-proof";
    private static final String TRANSFER_KEY = "tmp/transfer/u-42/2aac932d-f68b-4491-9891-fd47671fcf02.jpg";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private SettlementService settlementService;

    @MockitoBean
    private TransferProofService transferProofService;

    @MockitoBean
    private SettlementOverviewService settlementOverviewService;

    @MockitoBean
    private MeterImageService meterImageService;

    @MockitoBean
    private TransferProofSubmissionService transferProofSubmissionService;

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
    @DisplayName("미터기 사진을 내면 202와 분석 상태 · 시작 시각 · 남은 횟수를 내린다")
    void submitMeterImage() throws Exception {
        given(meterImageService.submit(42L, 77L, METER_KEY)).willReturn(new MeterAnalysis(SettlementStatus.PENDING,
                MeterAnalysisStatus.ANALYZING, null, LocalDateTime.of(2026, 9, 7, 9, 0, 3), 1));

        mockMvc.perform(submitMeter("{\"meter_image_key\":\"" + METER_KEY + "\"}").with(member()))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.message").value("미터기 금액 분석 상태를 확인하세요."))
                .andExpect(jsonPath("$.data.group_settlement_status").value("PENDING"))
                .andExpect(jsonPath("$.data.meter_analysis_status").value("ANALYZING"))
                .andExpect(jsonPath("$.data.total_amount").isEmpty())
                .andExpect(jsonPath("$.data.analysis_started_at").value("2026-09-07T09:00:03"))
                .andExpect(jsonPath("$.data.remaining_meter_attempts").value(1));
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {"{}", "{\"meter_image_key\":null}", "{\"meter_image_key\":\" \"}"})
    @DisplayName("사진 키가 없으면 422 VALIDATION_ERROR다")
    void missingMeterImageKey(String body) throws Exception {
        mockMvc.perform(submitMeter(body).with(member()))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.error.field").value("meter_image_key"));

        verifyNoInteractions(meterImageService);
    }

    @ParameterizedTest(name = "{1}")
    @MethodSource("meterSubmitFailures")
    @DisplayName("미터기 사진 제출을 서비스가 거절하면 그 상태와 코드를 내린다")
    void meterSubmitRejected(ErrorCode errorCode, String expectedCode, int expectedStatus) throws Exception {
        given(meterImageService.submit(42L, 77L, METER_KEY)).willThrow(new BusinessException(errorCode));

        mockMvc.perform(submitMeter("{\"meter_image_key\":\"" + METER_KEY + "\"}").with(member()))
                .andExpect(status().is(expectedStatus))
                .andExpect(jsonPath("$.error.code").value(expectedCode));
    }

    static Stream<Arguments> meterSubmitFailures() {
        return Stream.of(
                Arguments.of(SettlementErrorCode.SETTLEMENT_NOT_FOUND, "SETTLEMENT_NOT_FOUND", 404),
                Arguments.of(TaxiPotErrorCode.HOST_ONLY, "HOST_ONLY", 403),
                Arguments.of(SettlementErrorCode.ALREADY_REQUESTED, "ALREADY_REQUESTED", 409),
                Arguments.of(SettlementErrorCode.METER_ANALYSIS_IN_PROGRESS, "METER_ANALYSIS_IN_PROGRESS", 409),
                Arguments.of(SettlementErrorCode.RETRY_LIMIT_EXCEEDED, "RETRY_LIMIT_EXCEEDED", 409),
                Arguments.of(ImageErrorCode.IMAGE_NOT_EXISTS, "IMAGE_NOT_EXISTS", 422));
    }

    @Test
    @DisplayName("로그인하지 않고 미터기 사진을 내면 401이다")
    void submitMeterUnauthenticated() throws Exception {
        mockMvc.perform(submitMeter("{\"meter_image_key\":\"" + METER_KEY + "\"}"))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(meterImageService);
    }

    @Test
    @DisplayName("미터기 사진이 있으면 200과 분석 상태 · 만료형 주소를 내린다")
    void meterImage() throws Exception {
        given(meterImageService.find(42L, 77L)).willReturn(new MeterImage(MeterAnalysisStatus.ANALYZING,
                "https://bucket.s3.amazonaws.com/meter/a.jpg?X-Amz-Expires=1800"));

        mockMvc.perform(get(METER_IMAGE).with(member()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("미터기 사진을 조회했습니다"))
                .andExpect(jsonPath("$.data.meter_analysis_status").value("ANALYZING"))
                .andExpect(jsonPath("$.data.image_url").value("https://bucket.s3.amazonaws.com/meter/a.jpg?X-Amz-Expires=1800"));
    }

    @Test
    @DisplayName("미터기 사진이 없으면 분석 상태만 내리고 주소 키는 없다")
    void meterImageNotSubmitted() throws Exception {
        given(meterImageService.find(42L, 77L)).willReturn(new MeterImage(MeterAnalysisStatus.MANUAL, null));

        mockMvc.perform(get(METER_IMAGE).with(member()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.meter_analysis_status").value("MANUAL"))
                .andExpect(jsonPath("$.data", not(hasKey("image_url"))));
    }

    @Test
    @DisplayName("미터기 사진을 볼 수 없는 정산이면 404 SETTLEMENT_NOT_FOUND다")
    void meterImageNotFound() throws Exception {
        given(meterImageService.find(42L, 77L))
                .willThrow(new BusinessException(SettlementErrorCode.SETTLEMENT_NOT_FOUND));

        mockMvc.perform(get(METER_IMAGE).with(member()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("SETTLEMENT_NOT_FOUND"));
    }

    @Test
    @DisplayName("미터기 분석 중이면 200과 정산 상태 · 분석 상태 · 분석 시작 시각을 내린다")
    void meterAnalyzing() throws Exception {
        given(settlementService.findMeterAnalysis(42L, 77L)).willReturn(new MeterAnalysis(SettlementStatus.PENDING,
                MeterAnalysisStatus.ANALYZING, null, LocalDateTime.of(2026, 9, 7, 9, 0, 3), 1));

        mockMvc.perform(get(ANALYSIS_STATUS).with(member()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("미터기 금액 인식 상태를 조회했습니다"))
                .andExpect(jsonPath("$.data.group_settlement_status").value("PENDING"))
                .andExpect(jsonPath("$.data.meter_analysis_status").value("ANALYZING"))
                .andExpect(jsonPath("$.data", hasKey("total_amount")))
                .andExpect(jsonPath("$.data.total_amount").isEmpty())
                .andExpect(jsonPath("$.data.analysis_started_at").value("2026-09-07T09:00:03"))
                .andExpect(jsonPath("$.data.remaining_meter_attempts").value(1));
    }

    @Test
    @DisplayName("금액이 정해지면 총액을 내린다")
    void meterAmountDecided() throws Exception {
        given(settlementService.findMeterAnalysis(42L, 77L)).willReturn(new MeterAnalysis(SettlementStatus.REQUESTED,
                MeterAnalysisStatus.MANUAL, 18000, null, 2));

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
                MeterAnalysisStatus.PENDING, null, null, 2));

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

    @Test
    @DisplayName("청구를 받은 동승자에게 200과 보낼 금액 · 송금 상태 · 방장 계좌를 내린다")
    void myTransferCharged() throws Exception {
        given(transferProofService.findMine(42L, 77L)).willReturn(new MyTransfer(3, SettlementStatus.REQUESTED,
                Optional.of(new MyTransfer.Charge(6000, TransferAnalysisStatus.PENDING, null,
                        new MyTransfer.Payee(7L, "김방장", "kb", "11012345678")))));

        mockMvc.perform(get(MY_TRANSFER).with(member()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("내 송금 상태를 조회했습니다"))
                .andExpect(jsonPath("$.data.participant_count").value(3))
                .andExpect(jsonPath("$.data.group_settlement_status").value("REQUESTED"))
                .andExpect(jsonPath("$.data.member_analysis_status").value("PENDING"))
                .andExpect(jsonPath("$.data.amount").value(6000))
                .andExpect(jsonPath("$.data.payee.id").value(7))
                .andExpect(jsonPath("$.data.payee.name").value("김방장"))
                .andExpect(jsonPath("$.data.payee.bank_name").value("kb"))
                .andExpect(jsonPath("$.data.payee.account_no").value("11012345678"))
                .andExpect(jsonPath("$.data", not(hasKey("transferred_at"))))
                .andExpect(jsonPath("$.data", not(hasKey("is_forged"))));
    }

    @Test
    @DisplayName("송금이 확인되면 송금 시각을 내린다")
    void myTransferDone() throws Exception {
        given(transferProofService.findMine(42L, 77L)).willReturn(new MyTransfer(3, SettlementStatus.REQUESTED,
                Optional.of(new MyTransfer.Charge(6000, TransferAnalysisStatus.PASSED,
                        LocalDateTime.of(2026, 9, 7, 9, 1), new MyTransfer.Payee(7L, "김방장", "kb", "11012345678")))));

        mockMvc.perform(get(MY_TRANSFER).with(member()))
                .andExpect(jsonPath("$.data.member_analysis_status").value("PASSED"))
                .andExpect(jsonPath("$.data.transferred_at").value("2026-09-07T09:01:00"));
    }

    @Test
    @DisplayName("금액이 정해지기 전이면 송금 관련 키 없이 인원과 정산 상태만 내린다")
    void myTransferBeforeCharge() throws Exception {
        given(transferProofService.findMine(42L, 77L))
                .willReturn(new MyTransfer(3, SettlementStatus.PENDING, Optional.empty()));

        mockMvc.perform(get(MY_TRANSFER).with(member()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.participant_count").value(3))
                .andExpect(jsonPath("$.data.group_settlement_status").value("PENDING"))
                .andExpect(jsonPath("$.data", not(hasKey("member_analysis_status"))))
                .andExpect(jsonPath("$.data", not(hasKey("amount"))))
                .andExpect(jsonPath("$.data", not(hasKey("payee"))));
    }

    @ParameterizedTest(name = "{1}")
    @MethodSource("myTransferFailures")
    @DisplayName("내 송금 상태를 볼 수 없으면 그 상태와 코드를 내린다")
    void myTransferRejected(ErrorCode errorCode, String expectedCode, int expectedStatus) throws Exception {
        given(transferProofService.findMine(42L, 77L)).willThrow(new BusinessException(errorCode));

        mockMvc.perform(get(MY_TRANSFER).with(member()))
                .andExpect(status().is(expectedStatus))
                .andExpect(jsonPath("$.error.code").value(expectedCode));
    }

    static Stream<Arguments> myTransferFailures() {
        return Stream.of(
                Arguments.of(SettlementErrorCode.PAYEE_HAS_NO_TRANSFER, "PAYEE_HAS_NO_TRANSFER", 403),
                Arguments.of(SettlementErrorCode.SETTLEMENT_NOT_FOUND, "SETTLEMENT_NOT_FOUND", 404));
    }

    @Test
    @DisplayName("방장이 정산을 완료하면 204다")
    void complete() throws Exception {
        mockMvc.perform(post(COMPLETION).with(member()))
                .andExpect(status().isNoContent());

        verify(settlementService).complete(42L, 77L);
    }

    @ParameterizedTest(name = "{1}")
    @MethodSource("completionFailures")
    @DisplayName("정산을 완료할 수 없으면 그 상태와 코드를 내린다")
    void completionRejected(ErrorCode errorCode, String expectedCode, int expectedStatus) throws Exception {
        willThrow(new BusinessException(errorCode)).given(settlementService).complete(42L, 77L);

        mockMvc.perform(post(COMPLETION).with(member()))
                .andExpect(status().is(expectedStatus))
                .andExpect(jsonPath("$.error.code").value(expectedCode));
    }

    static Stream<Arguments> completionFailures() {
        return Stream.of(
                Arguments.of(TaxiPotErrorCode.HOST_ONLY, "HOST_ONLY", 403),
                Arguments.of(SettlementErrorCode.SETTLEMENT_NOT_FOUND, "SETTLEMENT_NOT_FOUND", 404),
                Arguments.of(SettlementErrorCode.INVALID_STATE_TRANSITION, "INVALID_STATE_TRANSITION", 409));
    }

    @Test
    @DisplayName("인증 사진이 있으면 200과 분석 상태 · 만료형 주소 · 송금 시각을 내린다")
    void transferProof() throws Exception {
        given(transferProofService.find(42L, 77L, 43L)).willReturn(new TransferProof(TransferAnalysisStatus.PASSED,
                "https://bucket.s3.amazonaws.com/transfer/a.jpg?X-Amz-Expires=1800", LocalDateTime.of(2026, 9, 7, 9, 1)));

        mockMvc.perform(get(TRANSFER_PROOF).with(member()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("송금 인증 사진을 조회했습니다"))
                .andExpect(jsonPath("$.data.member_analysis_status").value("PASSED"))
                .andExpect(jsonPath("$.data.image_url").value("https://bucket.s3.amazonaws.com/transfer/a.jpg?X-Amz-Expires=1800"))
                .andExpect(jsonPath("$.data.transferred_at").value("2026-09-07T09:01:00"))
                .andExpect(jsonPath("$.data", not(hasKey("is_forged"))));
    }

    @Test
    @DisplayName("아직 사진을 내지 않았으면 분석 상태만 내리고 주소 · 송금 시각 키는 없다")
    void transferProofNotSubmitted() throws Exception {
        given(transferProofService.find(42L, 77L, 43L))
                .willReturn(new TransferProof(TransferAnalysisStatus.PENDING, null, null));

        mockMvc.perform(get(TRANSFER_PROOF).with(member()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.member_analysis_status").value("PENDING"))
                .andExpect(jsonPath("$.data", not(hasKey("image_url"))))
                .andExpect(jsonPath("$.data", not(hasKey("transferred_at"))));
    }

    @ParameterizedTest(name = "{1}")
    @MethodSource("transferProofFailures")
    @DisplayName("인증 사진을 볼 수 없으면 그 상태와 코드를 내린다")
    void transferProofRejected(ErrorCode errorCode, String expectedCode, int expectedStatus) throws Exception {
        given(transferProofService.find(42L, 77L, 43L)).willThrow(new BusinessException(errorCode));

        mockMvc.perform(get(TRANSFER_PROOF).with(member()))
                .andExpect(status().is(expectedStatus))
                .andExpect(jsonPath("$.error.code").value(expectedCode));
    }

    static Stream<Arguments> transferProofFailures() {
        return Stream.of(
                Arguments.of(SettlementErrorCode.TRANSFER_PROOF_FORBIDDEN, "TRANSFER_PROOF_FORBIDDEN", 403),
                Arguments.of(SettlementErrorCode.SETTLEMENT_NOT_FOUND, "SETTLEMENT_NOT_FOUND", 404),
                Arguments.of(SettlementErrorCode.TRANSFER_NOT_FOUND, "TRANSFER_NOT_FOUND", 404));
    }

    @Test
    @DisplayName("방장이 조회하면 200과 정산 상태 · 동승자별 송금 상태를 내린다")
    void overview() throws Exception {
        given(settlementOverviewService.find(42L, 77L)).willReturn(new SettlementOverview(77L,
                SettlementStatus.REQUESTED, MeterAnalysisStatus.MANUAL, 10000, 3333, 3, null, List.of(
                        new SettlementOverview.Payer(43L, "이다라", "https://img/profile/a.png", 3333,
                                TransferAnalysisStatus.PASSED, LocalDateTime.of(2026, 9, 7, 9, 1), true),
                        new SettlementOverview.Payer(44L, "박마바", null, 3333,
                                TransferAnalysisStatus.PENDING, null, false))));

        mockMvc.perform(get(SETTLEMENT).with(member()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("정산 상태를 조회했습니다"))
                .andExpect(jsonPath("$.data.id").value(77))
                .andExpect(jsonPath("$.data.group_settlement_status").value("REQUESTED"))
                .andExpect(jsonPath("$.data.meter_analysis_status").value("MANUAL"))
                .andExpect(jsonPath("$.data.total_amount").value(10000))
                .andExpect(jsonPath("$.data.amount_per_person").value(3333))
                .andExpect(jsonPath("$.data.participant_count").value(3))
                .andExpect(jsonPath("$.data", not(hasKey("completed_at"))))
                .andExpect(jsonPath("$.data.participants[0].user_id").value(43))
                .andExpect(jsonPath("$.data.participants[0].name").value("이다라"))
                .andExpect(jsonPath("$.data.participants[0].profile_image_url").value("https://img/profile/a.png"))
                .andExpect(jsonPath("$.data.participants[0].amount").value(3333))
                .andExpect(jsonPath("$.data.participants[0].member_analysis_status").value("PASSED"))
                .andExpect(jsonPath("$.data.participants[0].transferred_at").value("2026-09-07T09:01:00"))
                .andExpect(jsonPath("$.data.participants[0].has_transfer_proof").value(true))
                .andExpect(jsonPath("$.data.participants[1].member_analysis_status").value("PENDING"))
                .andExpect(jsonPath("$.data.participants[1].has_transfer_proof").value(false))
                .andExpect(jsonPath("$.data.participants[1]", not(hasKey("transferred_at"))));
    }

    @Test
    @DisplayName("금액이 정해지기 전이면 인당 금액 키가 없고 총액은 null, 참여자는 빈 목록이다")
    void overviewBeforeAmount() throws Exception {
        given(settlementOverviewService.find(42L, 77L)).willReturn(new SettlementOverview(77L,
                SettlementStatus.PENDING, MeterAnalysisStatus.PENDING, null, null, 3, null, List.of()));

        mockMvc.perform(get(SETTLEMENT).with(member()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasKey("total_amount")))
                .andExpect(jsonPath("$.data.total_amount").isEmpty())
                .andExpect(jsonPath("$.data", not(hasKey("amount_per_person"))))
                .andExpect(jsonPath("$.data.participants").isArray())
                .andExpect(jsonPath("$.data.participants").isEmpty());
    }

    @Test
    @DisplayName("정산을 완료했으면 완료 시각을 내린다")
    void overviewCompleted() throws Exception {
        given(settlementOverviewService.find(42L, 77L)).willReturn(new SettlementOverview(77L,
                SettlementStatus.COMPLETED, MeterAnalysisStatus.MANUAL, 10000, 3333, 3,
                LocalDateTime.of(2026, 9, 7, 9, 30), List.of()));

        mockMvc.perform(get(SETTLEMENT).with(member()))
                .andExpect(jsonPath("$.data.group_settlement_status").value("COMPLETED"))
                .andExpect(jsonPath("$.data.completed_at").value("2026-09-07T09:30:00"));
    }

    @ParameterizedTest(name = "{1}")
    @MethodSource("overviewFailures")
    @DisplayName("정산 상태를 볼 수 없으면 그 상태와 코드를 내린다")
    void overviewRejected(ErrorCode errorCode, String expectedCode, int expectedStatus) throws Exception {
        given(settlementOverviewService.find(42L, 77L)).willThrow(new BusinessException(errorCode));

        mockMvc.perform(get(SETTLEMENT).with(member()))
                .andExpect(status().is(expectedStatus))
                .andExpect(jsonPath("$.error.code").value(expectedCode));
    }

    static Stream<Arguments> overviewFailures() {
        return Stream.of(
                Arguments.of(TaxiPotErrorCode.HOST_ONLY, "HOST_ONLY", 403),
                Arguments.of(SettlementErrorCode.SETTLEMENT_NOT_FOUND, "SETTLEMENT_NOT_FOUND", 404));
    }

    @Test
    @DisplayName("송금 인증사진을 내면 202와 정산 상태 · 남은 제출 횟수를 내린다")
    void submitTransferProof() throws Exception {
        given(transferProofSubmissionService.submit(42L, 77L, TRANSFER_KEY))
                .willReturn(new TransferSubmission(SettlementStatus.REQUESTED, 2));

        mockMvc.perform(submitTransfer("{\"transfer_image_key\":\"" + TRANSFER_KEY + "\"}").with(member()))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.message").value("AI 송금 분석 상태를 확인하세요."))
                .andExpect(jsonPath("$.data.group_settlement_status").value("REQUESTED"))
                .andExpect(jsonPath("$.data.remaining_transfer_attempts").value(2))
                .andExpect(jsonPath("$.data", not(hasKey("member_analysis_status"))));
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {"{}", "{\"transfer_image_key\":null}", "{\"transfer_image_key\":\" \"}"})
    @DisplayName("송금 사진 키가 없으면 422 VALIDATION_ERROR다")
    void missingTransferImageKey(String body) throws Exception {
        mockMvc.perform(submitTransfer(body).with(member()))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.error.field").value("transfer_image_key"));

        verifyNoInteractions(transferProofSubmissionService);
    }

    @ParameterizedTest(name = "{1}")
    @MethodSource("transferSubmitFailures")
    @DisplayName("송금 인증사진 제출을 서비스가 거절하면 그 상태와 코드를 내린다")
    void transferSubmitRejected(ErrorCode errorCode, String expectedCode, int expectedStatus) throws Exception {
        given(transferProofSubmissionService.submit(42L, 77L, TRANSFER_KEY)).willThrow(new BusinessException(errorCode));

        mockMvc.perform(submitTransfer("{\"transfer_image_key\":\"" + TRANSFER_KEY + "\"}").with(member()))
                .andExpect(status().is(expectedStatus))
                .andExpect(jsonPath("$.error.code").value(expectedCode));
    }

    static Stream<Arguments> transferSubmitFailures() {
        return Stream.of(
                Arguments.of(SettlementErrorCode.SETTLEMENT_NOT_FOUND, "SETTLEMENT_NOT_FOUND", 404),
                Arguments.of(SettlementErrorCode.PAYEE_HAS_NO_TRANSFER, "PAYEE_HAS_NO_TRANSFER", 403),
                Arguments.of(SettlementErrorCode.TRANSFER_NOT_SUBMITTABLE, "TRANSFER_NOT_SUBMITTABLE", 409),
                Arguments.of(SettlementErrorCode.TRANSFER_ANALYSIS_IN_PROGRESS, "TRANSFER_ANALYSIS_IN_PROGRESS", 409),
                Arguments.of(SettlementErrorCode.ALREADY_TRANSFERRED, "ALREADY_TRANSFERRED", 409),
                Arguments.of(SettlementErrorCode.TRANSFER_RETRY_LIMIT_EXCEEDED, "TRANSFER_RETRY_LIMIT_EXCEEDED", 409),
                Arguments.of(ImageErrorCode.IMAGE_NOT_EXISTS, "IMAGE_NOT_EXISTS", 422));
    }

    @Test
    @DisplayName("로그인하지 않고 송금 인증사진을 내면 401이다")
    void submitTransferUnauthenticated() throws Exception {
        mockMvc.perform(submitTransfer("{\"transfer_image_key\":\"" + TRANSFER_KEY + "\"}"))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(transferProofSubmissionService);
    }

    private static MockHttpServletRequestBuilder submitTransfer(String body) {
        return post(TRANSFER_SUBMIT).contentType(MediaType.APPLICATION_JSON).content(body);
    }

    private static MockHttpServletRequestBuilder submitMeter(String body) {
        return post(METER_IMAGE).contentType(MediaType.APPLICATION_JSON).content(body);
    }

    private static MockHttpServletRequestBuilder inputAmount(String body) {
        return put("/api/settlements/77/amount").contentType(MediaType.APPLICATION_JSON).content(body);
    }

    private static RequestPostProcessor member() {
        return jwt().jwt(builder -> builder.subject("42")).authorities(Authority.USER);
    }
}
