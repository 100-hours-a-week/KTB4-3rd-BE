package com.ktb.moyeota.domain.congestion.controller;

import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ktb.moyeota.domain.congestion.document.SpotType;
import com.ktb.moyeota.domain.congestion.dto.CongestionSpotItem;
import com.ktb.moyeota.domain.congestion.dto.CongestionSpotSearchRequest;
import com.ktb.moyeota.domain.congestion.dto.CongestionSpotSearchResponse;
import com.ktb.moyeota.domain.congestion.error.CongestionErrorCode;
import com.ktb.moyeota.domain.congestion.service.CongestionQueryService;
import com.ktb.moyeota.domain.congestion.service.CongestionSpotDetail;
import com.ktb.moyeota.domain.congestion.status.CongestionStatus;
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
import com.ktb.moyeota.global.security.resolver.AuthUserOptionalArgumentResolver;
import com.ktb.moyeota.global.security.resolver.SignupPrincipalArgumentResolver;
import com.ktb.moyeota.global.security.resolver.UploadScopeArgumentResolver;
import com.ktb.moyeota.global.security.signup.SignupSessionAuthenticator;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

@WebMvcTest(controllers = CongestionController.class)
@Import({SecurityConfig.class, CorsConfig.class, JwtConfig.class, ClockConfig.class, WebConfig.class,
        AuthUserArgumentResolver.class, AuthUserOptionalArgumentResolver.class, SignupPrincipalArgumentResolver.class,
        UploadScopeArgumentResolver.class, ApiAuthenticationEntryPoint.class, ApiAccessDeniedHandler.class,
        GlobalExceptionHandler.class})
@EnableConfigurationProperties({AuthProperties.class, CorsProperties.class})
class CongestionControllerTest {

    private static final Instant WINDOW_END = Instant.parse("2026-11-02T09:10:00Z");

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private CongestionQueryService queryService;

    @MockitoBean
    private SignupSessionAuthenticator signupSessionAuthenticator;

    @Test
    @DisplayName("핀 상세를 목록 핀과 같은 필드에 원인 문장과 분석 중을 더해 돌려준다")
    void returnsSpotDetail() throws Exception {
        given(queryService.findSpot(101L)).willReturn(detail("출퇴근하는 사람이 많아요.", false));

        mockMvc.perform(get("/api/congestion/spots/101").with(member()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("조회에 성공했습니다"))
                .andExpect(jsonPath("$.data.id").value(101))
                .andExpect(jsonPath("$.data.type").value("STATION"))
                .andExpect(jsonPath("$.data.name").value("판교역"))
                .andExpect(jsonPath("$.data.lat").value(37.3948))
                .andExpect(jsonPath("$.data.lng").value(127.1112))
                .andExpect(jsonPath("$.data.status").value("CONGESTED"))
                .andExpect(jsonPath("$.data.window_end").value("2026-11-02T18:10:00"))
                .andExpect(jsonPath("$.data.reason").value("출퇴근하는 사람이 많아요."))
                .andExpect(jsonPath("$.data.analyzing").value(false));
    }

    @Test
    @DisplayName("원인 문장이 없으면 혼잡 원인 정보 없음 문구로 돌려준다")
    void returnsReasonMissingMessage() throws Exception {
        given(queryService.findSpot(101L)).willReturn(detail(null, false));

        mockMvc.perform(get("/api/congestion/spots/101").with(member()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("혼잡 원인 정보 없음"))
                .andExpect(jsonPath("$.data.reason").isEmpty());
    }

    @Test
    @DisplayName("분석 중이면 원인 문장이 없어도 혼잡 원인 분석 중 문구로 돌려준다")
    void returnsAnalyzingMessageFirst() throws Exception {
        given(queryService.findSpot(101L)).willReturn(detail(null, true));

        mockMvc.perform(get("/api/congestion/spots/101").with(member()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("혼잡 원인 분석 중"))
                .andExpect(jsonPath("$.data.analyzing").value(true));
    }

    @Test
    @DisplayName("없는 스팟이면 404 SPOT_NOT_FOUND")
    void returnsNotFound() throws Exception {
        given(queryService.findSpot(999L)).willThrow(new BusinessException(CongestionErrorCode.SPOT_NOT_FOUND));

        mockMvc.perform(get("/api/congestion/spots/999").with(member()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("정보를 불러오지 못했어요"))
                .andExpect(jsonPath("$.error.code").value("SPOT_NOT_FOUND"));
    }

    @Test
    @DisplayName("로그인하지 않아도 핀 상세를 조회할 수 있다")
    void allowsAnonymousDetail() throws Exception {
        given(queryService.findSpot(101L)).willReturn(detail("출퇴근하는 사람이 많아요.", false));

        mockMvc.perform(get("/api/congestion/spots/101"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(101));
    }

    @Test
    @DisplayName("snake_case 지도 범위로 조회하면 스팟 목록을 명세서 형식으로 돌려준다")
    void returnsSpots() throws Exception {
        CongestionSpotSearchRequest expected = new CongestionSpotSearchRequest(
                new BigDecimal("37.38"), new BigDecimal("127.10"), new BigDecimal("37.41"), new BigDecimal("127.13"));
        given(queryService.findSpotsInBounds(expected)).willReturn(new CongestionSpotSearchResponse(List.of(
                new CongestionSpotItem(101L, SpotType.STATION, "판교역", 37.3948, 127.1112, CongestionStatus.CROWDED,
                        LocalDateTime.parse("2026-11-04T07:10:00"))), 500, false));

        mockMvc.perform(get("/api/congestion/spots").with(member())
                        .param("sw_lat", "37.38").param("sw_lng", "127.10")
                        .param("ne_lat", "37.41").param("ne_lng", "127.13"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("조회에 성공했습니다"))
                .andExpect(jsonPath("$.data.items[0].id").value(101))
                .andExpect(jsonPath("$.data.items[0].type").value("STATION"))
                .andExpect(jsonPath("$.data.items[0].name").value("판교역"))
                .andExpect(jsonPath("$.data.items[0].lat").value(37.3948))
                .andExpect(jsonPath("$.data.items[0].lng").value(127.1112))
                .andExpect(jsonPath("$.data.items[0].status").value("CROWDED"))
                .andExpect(jsonPath("$.data.items[0].window_end").value("2026-11-04T07:10:00"))
                .andExpect(jsonPath("$.data.items[0].score").doesNotExist())
                .andExpect(jsonPath("$.data.limit").value(500))
                .andExpect(jsonPath("$.data.limit_exceeded").value(false));
    }

    @Test
    @DisplayName("지도 범위 값이 빠지면 검증 실패로 거부하고 서비스를 부르지 않는다")
    void rejectsMissingBounds() throws Exception {
        mockMvc.perform(get("/api/congestion/spots").with(member())
                        .param("sw_lng", "127.10").param("ne_lat", "37.41").param("ne_lng", "127.13"))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.error.field").value("sw_lat"));

        verifyNoInteractions(queryService);
    }

    @Test
    @DisplayName("로그인하지 않아도 스팟 목록을 조회할 수 있다")
    void allowsAnonymousSpots() throws Exception {
        CongestionSpotSearchRequest expected = new CongestionSpotSearchRequest(
                new BigDecimal("37.38"), new BigDecimal("127.10"), new BigDecimal("37.41"), new BigDecimal("127.13"));
        given(queryService.findSpotsInBounds(expected)).willReturn(new CongestionSpotSearchResponse(List.of(), 500, false));

        mockMvc.perform(get("/api/congestion/spots")
                        .param("sw_lat", "37.38").param("sw_lng", "127.10")
                        .param("ne_lat", "37.41").param("ne_lng", "127.13"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.limit").value(500));
    }

    private static CongestionSpotDetail detail(String reason, boolean analyzing) {
        return new CongestionSpotDetail(101L, SpotType.STATION, "판교역", 37.3948, 127.1112,
                CongestionStatus.CONGESTED, WINDOW_END, reason, analyzing);
    }

    private static RequestPostProcessor member() {
        return jwt().jwt(builder -> builder.subject("42")).authorities(Authority.USER);
    }
}
