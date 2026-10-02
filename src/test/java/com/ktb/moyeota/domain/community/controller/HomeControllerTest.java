package com.ktb.moyeota.domain.community.controller;

import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ktb.moyeota.domain.community.dto.MapPinItem;
import com.ktb.moyeota.domain.community.dto.MapPinSearchRequest;
import com.ktb.moyeota.domain.community.dto.MapPinSearchResponse;
import com.ktb.moyeota.domain.community.dto.MapPinType;
import com.ktb.moyeota.domain.community.dto.NearbyPostSearchRequest;
import com.ktb.moyeota.domain.community.dto.NearbyPostSearchResponse;
import com.ktb.moyeota.domain.community.service.HomeService;
import com.ktb.moyeota.global.config.ClockConfig;
import com.ktb.moyeota.global.config.CorsConfig;
import com.ktb.moyeota.global.config.CorsProperties;
import com.ktb.moyeota.global.config.WebConfig;
import com.ktb.moyeota.global.exception.GlobalExceptionHandler;
import com.ktb.moyeota.global.security.AuthProperties;
import com.ktb.moyeota.global.security.SecurityConfig;
import com.ktb.moyeota.global.security.handler.ApiAccessDeniedHandler;
import com.ktb.moyeota.global.security.handler.ApiAuthenticationEntryPoint;
import com.ktb.moyeota.global.security.jwt.JwtConfig;
import com.ktb.moyeota.global.security.resolver.AuthUserArgumentResolver;
import com.ktb.moyeota.global.security.resolver.SignupPrincipalArgumentResolver;
import com.ktb.moyeota.global.security.resolver.UploadScopeArgumentResolver;
import com.ktb.moyeota.global.security.signup.SignupSessionAuthenticator;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(controllers = HomeController.class)
@Import({SecurityConfig.class, CorsConfig.class, JwtConfig.class, ClockConfig.class, WebConfig.class,
        AuthUserArgumentResolver.class, SignupPrincipalArgumentResolver.class, UploadScopeArgumentResolver.class,
        ApiAuthenticationEntryPoint.class, ApiAccessDeniedHandler.class, GlobalExceptionHandler.class})
@EnableConfigurationProperties({AuthProperties.class, CorsProperties.class})
class HomeControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private HomeService homeService;

    @MockitoBean
    private SignupSessionAuthenticator signupSessionAuthenticator;

    @Test
    @DisplayName("snake_case 쿼리 파라미터가 핀 조회 요청으로 바인딩된다")
    void mapPinsBindsSnakeCaseParams() throws Exception {
        MapPinSearchRequest expected = new MapPinSearchRequest(
                new BigDecimal("37.495"), new BigDecimal("127.025"),
                new BigDecimal("37.505"), new BigDecimal("127.035"));
        given(homeService.searchMapPins(expected)).willReturn(new MapPinSearchResponse(
                List.of(new MapPinItem(MapPinType.COMMUNITY, 7L, new BigDecimal("37.5"), new BigDecimal("127.03"))),
                500, false));

        mockMvc.perform(get("/api/map-pins")
                        .param("sw_lat", "37.495").param("sw_lng", "127.025")
                        .param("ne_lat", "37.505").param("ne_lng", "127.035"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[0].id").value(7))
                .andExpect(jsonPath("$.data.limit").value(500));
    }

    @Test
    @DisplayName("위도 하나만 범위를 벗어나도 핀 조회를 거부하고 서비스를 호출하지 않는다")
    void mapPinsRejectOutOfRangeLatitude() throws Exception {
        mockMvc.perform(get("/api/map-pins")
                        .param("sw_lat", "999999").param("sw_lng", "127.025")
                        .param("ne_lat", "37.505").param("ne_lng", "127.035"))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.error.field").value("sw_lat"))
                .andExpect(jsonPath("$.error.details.length()").value(1));

        verifyNoInteractions(homeService);
    }

    @Test
    @DisplayName("좌표가 빠진 핀 조회는 검증 실패로 거부한다")
    void mapPinsRejectMissingCoordinate() throws Exception {
        mockMvc.perform(get("/api/map-pins")
                        .param("sw_lat", "37.495").param("sw_lng", "127.025").param("ne_lat", "37.505"))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.error.field").value("ne_lng"));
    }

    @Test
    @DisplayName("snake_case 쿼리 파라미터가 주변 게시글 요청으로 바인딩된다")
    void nearbyPostsBindsSnakeCaseParams() throws Exception {
        NearbyPostSearchRequest expected = new NearbyPostSearchRequest(
                new BigDecimal("37.5"), new BigDecimal("127.03"),
                new BigDecimal("37.495"), new BigDecimal("127.025"),
                new BigDecimal("37.505"), new BigDecimal("127.035"), "abc");
        given(homeService.searchNearbyPosts(expected)).willReturn(new NearbyPostSearchResponse(List.of(), null));

        mockMvc.perform(get("/api/nearby-posts")
                        .param("lat", "37.5").param("lng", "127.03")
                        .param("sw_lat", "37.495").param("sw_lng", "127.025")
                        .param("ne_lat", "37.505").param("ne_lng", "127.035")
                        .param("cursor", "abc"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items").isEmpty());
    }

    @Test
    @DisplayName("기준 위도 하나만 범위를 벗어나도 주변 게시글 조회를 거부하고 서비스를 호출하지 않는다")
    void nearbyPostsRejectOutOfRangeLatitude() throws Exception {
        mockMvc.perform(get("/api/nearby-posts")
                        .param("lat", "100").param("lng", "127.03")
                        .param("sw_lat", "37.495").param("sw_lng", "127.025")
                        .param("ne_lat", "37.505").param("ne_lng", "127.035"))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.error.field").value("lat"))
                .andExpect(jsonPath("$.error.details.length()").value(1));

        verifyNoInteractions(homeService);
    }
}
