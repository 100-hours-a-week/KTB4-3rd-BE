package com.ktb.moyeota.domain.report.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ktb.moyeota.domain.report.entity.Report;
import com.ktb.moyeota.domain.report.entity.ReportReason;
import com.ktb.moyeota.domain.report.error.ReportErrorCode;
import com.ktb.moyeota.domain.report.service.ReportService;
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
import java.util.LinkedHashMap;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

// [주의] 이번 단계는 메시지 신고(reported_message_id 필수)만 다룬다.
// 유저 단독 신고는 레이스 컨디션 이슈로 이번 커밋 범위에서 제외했다.
@WebMvcTest(controllers = ReportController.class)
@Import({SecurityConfig.class, CorsConfig.class, JwtConfig.class, ClockConfig.class, WebConfig.class,
        AuthUserArgumentResolver.class, SignupPrincipalArgumentResolver.class, UploadScopeArgumentResolver.class,
        ApiAuthenticationEntryPoint.class, ApiAccessDeniedHandler.class, GlobalExceptionHandler.class})
@EnableConfigurationProperties({AuthProperties.class, CorsProperties.class})
class ReportControllerTest {

    private static final Long MESSAGE_ID = 1441L;

    @Autowired
    private MockMvc mockMvc;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @MockitoBean
    private ReportService reportService;

    @MockitoBean
    private SignupSessionAuthenticator signupSessionAuthenticator;

    private String body(Long reportedUserId, Long reportedMessageId, String reason, String reasonText)
            throws Exception {
        LinkedHashMap<String, Object> map = new LinkedHashMap<>();
        map.put("reported_user_id", reportedUserId);
        map.put("reported_message_id", reportedMessageId);
        map.put("reason", reason);
        map.put("reason_text", reasonText);
        return objectMapper.writeValueAsString(map);
    }

    private static Report reportWithId(Long id) {
        Report saved = Report.createMessageReport(null, null, null, ReportReason.ABUSE, null);
        ReflectionTestUtils.setField(saved, "id", id);
        ReflectionTestUtils.setField(saved, "createdAt", LocalDateTime.of(2026, 9, 6, 9, 0, 0));
        return saved;
    }

    @Test
    @DisplayName("신고하면 201과 id/created_at을 응답한다")
    void create() throws Exception {
        given(reportService.create(eq(42L), any())).willReturn(reportWithId(4L));

        mockMvc.perform(post("/api/reports").with(member())
                        .contentType("application/json")
                        .content(body(7L, MESSAGE_ID, "ABUSE", null)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.message").value("신고가 접수되었습니다"))
                .andExpect(jsonPath("$.data.id").value(4))
                .andExpect(jsonPath("$.data.created_at").exists());
    }

    @Test
    @DisplayName("액세스 토큰이 없으면 401이고 서비스를 부르지 않는다")
    void createAnonymous() throws Exception {
        mockMvc.perform(post("/api/reports")
                        .contentType("application/json")
                        .content(body(7L, MESSAGE_ID, "ABUSE", null)))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(reportService);
    }

    @Test
    @DisplayName("필수 필드(reported_user_id/reported_message_id/reason)가 없으면 422 VALIDATION_ERROR다 (공통 Bean Validation 컨벤션)")
    void missingRequiredField() throws Exception {
        mockMvc.perform(post("/api/reports").with(member())
                        .contentType("application/json")
                        .content(body(null, null, null, null)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));

        verifyNoInteractions(reportService);
    }

    @Test
    @DisplayName("reason=ETC인데 reason_text가 없으면 400 VALIDATION_ERROR(field: reason_text)다 (API 명세서 기준)")
    void reasonTextRequired() throws Exception {
        given(reportService.create(eq(42L), any()))
                .willThrow(new BusinessException(ReportErrorCode.REASON_TEXT_REQUIRED));

        mockMvc.perform(post("/api/reports").with(member())
                        .contentType("application/json")
                        .content(body(7L, MESSAGE_ID, "ETC", null)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("기타 사유를 입력해주세요"))
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.error.field").value("reason_text"));
    }

    @Test
    @DisplayName("이미 신고한 메시지면 409 DUPLICATE_REPORT(field: reported_message_id)다 (API 명세서 기준)")
    void duplicateMessageReport() throws Exception {
        given(reportService.create(eq(42L), any()))
                .willThrow(new BusinessException(ReportErrorCode.DUPLICATE_MESSAGE_REPORT));

        mockMvc.perform(post("/api/reports").with(member())
                        .contentType("application/json")
                        .content(body(7L, MESSAGE_ID, "ABUSE", null)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("이미 신고한 메시지입니다"))
                .andExpect(jsonPath("$.error.code").value("DUPLICATE_REPORT"))
                .andExpect(jsonPath("$.error.field").value("reported_message_id"));
    }

    private static RequestPostProcessor member() {
        return jwt().jwt(builder -> builder.subject("42")).authorities(Authority.USER);
    }
}
