package com.ktb.moyeota.domain.carpool.controller;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ktb.moyeota.domain.carpool.error.CarpoolErrorCode;
import com.ktb.moyeota.domain.carpool.model.CarpoolDetail;
import com.ktb.moyeota.domain.carpool.model.CarpoolDetail.Member;
import com.ktb.moyeota.domain.carpool.model.CarpoolPin;
import com.ktb.moyeota.domain.carpool.model.CarpoolPins;
import com.ktb.moyeota.domain.carpool.model.NearbyCarpool;
import com.ktb.moyeota.domain.carpool.model.NearbyCarpoolQuery;
import com.ktb.moyeota.domain.carpool.model.NearbyCarpools;
import com.ktb.moyeota.domain.carpool.service.CarpoolRideService;
import com.ktb.moyeota.domain.carpool.service.CarpoolService;
import com.ktb.moyeota.domain.companion.entity.CompanionStatus;
import com.ktb.moyeota.domain.companion.error.CompanionErrorCode;
import com.ktb.moyeota.global.common.Viewport;
import com.ktb.moyeota.global.config.ClockConfig;
import com.ktb.moyeota.global.config.CorsConfig;
import com.ktb.moyeota.global.config.CorsProperties;
import com.ktb.moyeota.global.config.WebConfig;
import com.ktb.moyeota.global.exception.BusinessException;
import com.ktb.moyeota.global.exception.CommonErrorCode;
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
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

@WebMvcTest(controllers = CarpoolController.class)
@Import({SecurityConfig.class, CorsConfig.class, JwtConfig.class, ClockConfig.class, WebConfig.class,
        AuthUserArgumentResolver.class, SignupPrincipalArgumentResolver.class, UploadScopeArgumentResolver.class,
        ApiAuthenticationEntryPoint.class, ApiAccessDeniedHandler.class, GlobalExceptionHandler.class})
@EnableConfigurationProperties({AuthProperties.class, CorsProperties.class})
class CarpoolControllerTest {

    private static final Viewport VIEWPORT = new Viewport(
            new BigDecimal("37.39"), new BigDecimal("127.10"), new BigDecimal("37.40"), new BigDecimal("127.12"));

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private CarpoolService carpoolService;

    @MockitoBean
    private CarpoolRideService carpoolRideService;

    @MockitoBean
    private SignupSessionAuthenticator signupSessionAuthenticator;

    @Test
    @DisplayName("snake_case 뷰포트로 카풀 핀을 조회해 id와 출발지 좌표를 내린다")
    void findsPins() throws Exception {
        given(carpoolService.findPins(VIEWPORT)).willReturn(new CarpoolPins(
                List.of(new CarpoolPin(51L, new BigDecimal("37.394500"), new BigDecimal("127.111200"))), 500, false));

        mockMvc.perform(pins("37.39", "127.10", "37.40", "127.12").with(member()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("조회에 성공했습니다"))
                .andExpect(jsonPath("$.data.items[0].id").value(51))
                .andExpect(jsonPath("$.data.items[0].lat").value(37.3945))
                .andExpect(jsonPath("$.data.items[0].lng").value(127.1112))
                .andExpect(jsonPath("$.data.limit").value(500))
                .andExpect(jsonPath("$.data.limit_exceeded").value(false));
    }

    @Test
    @DisplayName("핀이 상한을 넘으면 빈 목록과 확대 안내 문구를 내린다")
    void tooManyPins() throws Exception {
        given(carpoolService.findPins(VIEWPORT)).willReturn(new CarpoolPins(List.of(), 500, true));

        mockMvc.perform(pins("37.39", "127.10", "37.40", "127.12").with(member()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("표시할 핀이 많습니다. 지도를 확대해주세요"))
                .andExpect(jsonPath("$.data.items").isEmpty())
                .andExpect(jsonPath("$.data.limit_exceeded").value(true));
    }

    @Test
    @DisplayName("좌표가 빠지면 422 VALIDATION_ERROR이고 조회하지 않는다")
    void missingCoordinate() throws Exception {
        mockMvc.perform(get("/api/carpool-pins")
                        .param("sw_lat", "37.39").param("sw_lng", "127.10").param("ne_lat", "37.40")
                        .with(member()))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.error.field").value("ne_lng"));

        verifyNoInteractions(carpoolService);
    }

    @Test
    @DisplayName("위도가 범위를 벗어나면 422 OUT_OF_RANGE이고 조회하지 않는다")
    void latitudeOutOfRange() throws Exception {
        mockMvc.perform(pins("91", "127.10", "37.40", "127.12").with(member()))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.error.details[0].field").value("sw_lat"))
                .andExpect(jsonPath("$.error.details[0].reason").value("OUT_OF_RANGE"));

        verifyNoInteractions(carpoolService);
    }

    @Test
    @DisplayName("남서 좌표가 북동 좌표보다 크면 400 VIEWPORT_OUT_OF_RANGE다")
    void invertedViewport() throws Exception {
        mockMvc.perform(pins("37.40", "127.10", "37.39", "127.12").with(member()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("지도 영역이 올바르지 않습니다"))
                .andExpect(jsonPath("$.error.code").value("VIEWPORT_OUT_OF_RANGE"));

        verifyNoInteractions(carpoolService);
    }

    @Test
    @DisplayName("뷰포트가 1도를 넘으면 400 VIEWPORT_TOO_LARGE다")
    void tooLargeViewport() throws Exception {
        mockMvc.perform(pins("37.0", "127.0", "38.5", "127.5").with(member()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("조회 범위가 너무 넓습니다. 지도를 확대해주세요"))
                .andExpect(jsonPath("$.error.code").value("VIEWPORT_TOO_LARGE"));

        verifyNoInteractions(carpoolService);
    }

    @Test
    @DisplayName("로그인하지 않아도 핀을 조회한다")
    void anonymous() throws Exception {
        given(carpoolService.findPins(VIEWPORT)).willReturn(new CarpoolPins(List.of(), 500, false));

        mockMvc.perform(pins("37.39", "127.10", "37.40", "127.12"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("조회에 성공했습니다"));
    }

    @Test
    @DisplayName("snake_case 쿼리로 주변 카풀을 조회해 방장 실명 · 거리 · 인원 · 마감 여부를 내린다")
    void findsNearby() throws Exception {
        NearbyCarpoolQuery query = new NearbyCarpoolQuery(
                new BigDecimal("37.3947"), new BigDecimal("127.1111"), VIEWPORT, "v1.next");
        given(carpoolService.findNearby(query)).willReturn(new NearbyCarpools(List.of(new NearbyCarpool(
                51L, "우림", null, "판교역", "강남역", LocalDateTime.of(2026, 9, 8, 8, 30),
                320.5, 2, 4, false, true)), "v1.after51"));

        mockMvc.perform(nearby("37.39", "127.10", "37.40", "127.12").param("cursor", "v1.next"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("조회에 성공했습니다"))
                .andExpect(jsonPath("$.data.items[0].id").value(51))
                .andExpect(jsonPath("$.data.items[0].host.name").value("우림"))
                .andExpect(content().string(not(containsString("nickname"))))
                .andExpect(jsonPath("$.data.items[0].host.profile_image_url").isEmpty())
                .andExpect(jsonPath("$.data.items[0].origin_name").value("판교역"))
                .andExpect(jsonPath("$.data.items[0].dest_name").value("강남역"))
                .andExpect(jsonPath("$.data.items[0].departure_at").value("2026-09-08T08:30:00"))
                .andExpect(jsonPath("$.data.items[0].distance_m").value(320.5))
                .andExpect(jsonPath("$.data.items[0].current_count").value(2))
                .andExpect(jsonPath("$.data.items[0].capacity").value(4))
                .andExpect(jsonPath("$.data.items[0].is_full").value(false))
                .andExpect(jsonPath("$.data.items[0].is_expired").value(true))
                .andExpect(jsonPath("$.data.next_cursor").value("v1.after51"));
    }

    @Test
    @DisplayName("다음 페이지가 없으면 next_cursor 는 null 이다")
    void lastPage() throws Exception {
        given(carpoolService.findNearby(any())).willReturn(new NearbyCarpools(List.of(), null));

        mockMvc.perform(nearby("37.39", "127.10", "37.40", "127.12"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items").isEmpty())
                .andExpect(jsonPath("$.data.next_cursor").isEmpty());
    }

    @Test
    @DisplayName("사용자 위치가 빠지면 422 VALIDATION_ERROR이고 조회하지 않는다")
    void nearbyMissingLocation() throws Exception {
        mockMvc.perform(get("/api/carpools")
                        .param("lng", "127.1111")
                        .param("sw_lat", "37.39").param("sw_lng", "127.10").param("ne_lat", "37.40").param("ne_lng", "127.12"))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.error.field").value("lat"))
                .andExpect(jsonPath("$.error.details[0].reason").value("REQUIRED"));

        verifyNoInteractions(carpoolService);
    }

    @Test
    @DisplayName("주변 목록도 뷰포트가 1도를 넘으면 400 VIEWPORT_TOO_LARGE다")
    void nearbyTooLargeViewport() throws Exception {
        mockMvc.perform(nearby("37.0", "127.0", "38.5", "127.5"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VIEWPORT_TOO_LARGE"));

        verifyNoInteractions(carpoolService);
    }

    @Test
    @DisplayName("커서가 잘못되면 400 INVALID_CURSOR다")
    void invalidCursor() throws Exception {
        given(carpoolService.findNearby(any())).willThrow(new BusinessException(CommonErrorCode.INVALID_CURSOR));

        mockMvc.perform(nearby("37.39", "127.10", "37.40", "127.12").param("cursor", "broken"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("잘못된 커서입니다."))
                .andExpect(jsonPath("$.error.code").value("INVALID_CURSOR"));
    }

    private static MockHttpServletRequestBuilder nearby(String swLat, String swLng, String neLat, String neLng) {
        return get("/api/carpools")
                .param("lat", "37.3947").param("lng", "127.1111")
                .param("sw_lat", swLat).param("sw_lng", swLng).param("ne_lat", neLat).param("ne_lng", neLng);
    }

    @Test
    @DisplayName("방장이 운행을 시작하면 카풀 상세와 같은 형식으로 바뀐 상태를 내린다")
    void changesStatus() throws Exception {
        given(carpoolRideService.changeStatus(42L, 51L, CompanionStatus.IN_PROGRESS)).willReturn(new CarpoolDetail(
                51L, CompanionStatus.IN_PROGRESS, new Member(42L, "우림", null), "판교역", "강남역",
                LocalDateTime.of(2026, 9, 8, 8, 30), "포르쉐 911", 2, 4, false,
                List.of(new Member(42L, "우림", null), new Member(9L, "루디", "https://cdn.moyeota.test/p/9.png"))));

        mockMvc.perform(statusChange(51L, "IN_PROGRESS").with(member()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("운행 상태가 변경됐어요"))
                .andExpect(jsonPath("$.data.id").value(51))
                .andExpect(jsonPath("$.data.status").value("IN_PROGRESS"))
                .andExpect(jsonPath("$.data.host.id").value(42))
                .andExpect(jsonPath("$.data.host.name").value("우림"))
                .andExpect(jsonPath("$.data.host.nickname").doesNotExist())
                .andExpect(jsonPath("$.data.car_model").value("포르쉐 911"))
                .andExpect(jsonPath("$.data.current_count").value(2))
                .andExpect(jsonPath("$.data.capacity").value(4))
                .andExpect(jsonPath("$.data.is_full").value(false))
                .andExpect(jsonPath("$.data.participants.length()").value(2))
                .andExpect(jsonPath("$.data.participants[1].profile_image_url").value("https://cdn.moyeota.test/p/9.png"))
                .andExpect(content().string(not(containsString("my_request"))));
    }

    @Test
    @DisplayName("상태 값이 IN_PROGRESS · COMPLETED 가 아니면 422 INVALID_ENUM이다")
    void rejectsUnknownStatus() throws Exception {
        mockMvc.perform(statusChange(51L, "CANCELED").with(member()))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.error.details[0].field").value("status"))
                .andExpect(jsonPath("$.error.details[0].reason").value("INVALID_ENUM"));

        verifyNoInteractions(carpoolRideService);
    }

    @Test
    @DisplayName("방장이 아니면 403 HOST_ONLY다")
    void hostOnly() throws Exception {
        given(carpoolRideService.changeStatus(any(), any(), any()))
                .willThrow(new BusinessException(CarpoolErrorCode.HOST_ONLY));

        mockMvc.perform(statusChange(51L, "IN_PROGRESS").with(member()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("카풀 등록자만 처리할 수 있습니다"))
                .andExpect(jsonPath("$.error.code").value("HOST_ONLY"));
    }

    @Test
    @DisplayName("참여 중인 카풀이 아니면 404 CARPOOL_NOT_FOUND다")
    void notFound() throws Exception {
        given(carpoolRideService.changeStatus(any(), any(), any()))
                .willThrow(new BusinessException(CarpoolErrorCode.CARPOOL_NOT_FOUND));

        mockMvc.perform(statusChange(51L, "IN_PROGRESS").with(member()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("CARPOOL_NOT_FOUND"));
    }

    @Test
    @DisplayName("혼자서 운행을 시작하면 409 NOT_ENOUGH_PARTICIPANTS다")
    void notEnoughParticipants() throws Exception {
        given(carpoolRideService.changeStatus(any(), any(), any()))
                .willThrow(new BusinessException(CompanionErrorCode.NOT_ENOUGH_PARTICIPANTS));

        mockMvc.perform(statusChange(51L, "IN_PROGRESS").with(member()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("NOT_ENOUGH_PARTICIPANTS"));
    }

    @Test
    @DisplayName("운행 상태 변경은 로그인해야 한다")
    void statusChangeRequiresLogin() throws Exception {
        mockMvc.perform(statusChange(51L, "IN_PROGRESS"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("UNAUTHORIZED"));

        verifyNoInteractions(carpoolRideService);
    }

    private static MockHttpServletRequestBuilder statusChange(Long carpoolId, String status) {
        return patch("/api/carpools/" + carpoolId)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"status\":\"" + status + "\"}");
    }

    private static MockHttpServletRequestBuilder pins(String swLat, String swLng, String neLat, String neLng) {
        return get("/api/carpool-pins")
                .param("sw_lat", swLat).param("sw_lng", swLng).param("ne_lat", neLat).param("ne_lng", neLng);
    }

    private static RequestPostProcessor member() {
        return jwt().jwt(builder -> builder.subject("42")).authorities(Authority.USER);
    }
}
