package com.ktb.moyeota.domain.carpool.controller;

import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ktb.moyeota.domain.carpool.model.CarpoolPin;
import com.ktb.moyeota.domain.carpool.model.CarpoolPins;
import com.ktb.moyeota.domain.carpool.service.CarpoolService;
import com.ktb.moyeota.global.common.Viewport;
import com.ktb.moyeota.global.config.ClockConfig;
import com.ktb.moyeota.global.config.CorsConfig;
import com.ktb.moyeota.global.config.CorsProperties;
import com.ktb.moyeota.global.config.WebConfig;
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
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
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

    private static MockHttpServletRequestBuilder pins(String swLat, String swLng, String neLat, String neLng) {
        return get("/api/carpool-pins")
                .param("sw_lat", swLat).param("sw_lng", swLng).param("ne_lat", neLat).param("ne_lng", neLng);
    }

    private static RequestPostProcessor member() {
        return jwt().jwt(builder -> builder.subject("42")).authorities(Authority.USER);
    }
}
