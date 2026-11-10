package com.ktb.moyeota.domain.user.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ktb.moyeota.domain.user.error.UserErrorCode;
import com.ktb.moyeota.domain.user.model.MyCar;
import com.ktb.moyeota.domain.user.model.OwnedCarCommand;
import com.ktb.moyeota.domain.user.service.OwnedCarService;
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
import org.springframework.test.web.servlet.request.RequestPostProcessor;

@WebMvcTest(controllers = OwnedCarController.class)
@Import({SecurityConfig.class, CorsConfig.class, JwtConfig.class, ClockConfig.class, WebConfig.class,
        AuthUserArgumentResolver.class, SignupPrincipalArgumentResolver.class, UploadScopeArgumentResolver.class,
        ApiAuthenticationEntryPoint.class, ApiAccessDeniedHandler.class, GlobalExceptionHandler.class})
@EnableConfigurationProperties({AuthProperties.class, CorsProperties.class})
class OwnedCarControllerTest {

    private static final String CARS = "/api/users/me/cars";
    private static final LocalDateTime CREATED_AT = LocalDateTime.of(2026, 9, 6, 9, 0);
    private static final LocalDateTime UPDATED_AT = LocalDateTime.of(2026, 9, 7, 10, 30);
    private static final OwnedCarCommand AVANTE = new OwnedCarCommand("아반떼", "12가3456");
    private static final String AVANTE_BODY = """
            {"model":"아반떼","number":"12가3456"}""";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private OwnedCarService ownedCarService;

    @MockitoBean
    private SignupSessionAuthenticator signupSessionAuthenticator;

    @Test
    @DisplayName("차량을 등록하면 201과 위치, 새 차량 id를 내린다")
    void register() throws Exception {
        given(ownedCarService.register(42L, AVANTE))
                .willReturn(new MyCar(3L, "아반떼", "12가3456", CREATED_AT, CREATED_AT));

        mockMvc.perform(post(CARS).contentType(MediaType.APPLICATION_JSON).content(AVANTE_BODY).with(member()))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "/api/users/me/cars/3"))
                .andExpect(jsonPath("$.message").value("차량이 등록되었습니다"))
                .andExpect(jsonPath("$.data.id").value(3))
                .andExpect(jsonPath("$.data.created_at").exists());
    }

    @Test
    @DisplayName("이미 차량이 있으면 409 CAR_ALREADY_REGISTERED다")
    void registerSecondCar() throws Exception {
        given(ownedCarService.register(42L, AVANTE))
                .willThrow(new BusinessException(UserErrorCode.CAR_ALREADY_REGISTERED));

        mockMvc.perform(post(CARS).contentType(MediaType.APPLICATION_JSON).content(AVANTE_BODY).with(member()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("이미 등록된 차량이 있습니다"))
                .andExpect(jsonPath("$.error.code").value("CAR_ALREADY_REGISTERED"));
    }

    @Test
    @DisplayName("차종이 빠지면 422 model REQUIRED이고 등록하지 않는다")
    void missingModel() throws Exception {
        mockMvc.perform(post(CARS).contentType(MediaType.APPLICATION_JSON).content("""
                        {"number":"12가3456"}""").with(member()))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.error.details[0].field").value("model"))
                .andExpect(jsonPath("$.error.details[0].reason").value("REQUIRED"));

        verifyNoInteractions(ownedCarService);
    }

    @Test
    @DisplayName("차량 번호가 20자를 넘으면 422 number LENGTH_OUT_OF_RANGE다")
    void tooLongNumber() throws Exception {
        mockMvc.perform(post(CARS).contentType(MediaType.APPLICATION_JSON).content("""
                        {"model":"아반떼","number":"%s"}""".formatted("1".repeat(21))).with(member()))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.error.details[0].field").value("number"))
                .andExpect(jsonPath("$.error.details[0].reason").value("LENGTH_OUT_OF_RANGE"));

        verifyNoInteractions(ownedCarService);
    }

    @Test
    @DisplayName("차량 정보를 수정하면 수정 시각을 내린다")
    void update() throws Exception {
        given(ownedCarService.update(42L, 3L, AVANTE))
                .willReturn(new MyCar(3L, "아반떼", "12가3456", CREATED_AT, UPDATED_AT));

        mockMvc.perform(put(CARS + "/3").contentType(MediaType.APPLICATION_JSON).content(AVANTE_BODY).with(member()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("차량 정보가 수정되었습니다"))
                .andExpect(jsonPath("$.data.updated_at").exists())
                .andExpect(jsonPath("$.data.id").doesNotExist());
    }

    @Test
    @DisplayName("내 차량이 아니면 수정은 404 CAR_NOT_FOUND다")
    void updateNotMine() throws Exception {
        given(ownedCarService.update(any(), any(), any()))
                .willThrow(new BusinessException(UserErrorCode.CAR_NOT_FOUND));

        mockMvc.perform(put(CARS + "/9").contentType(MediaType.APPLICATION_JSON).content(AVANTE_BODY).with(member()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("존재하지 않는 차량입니다"))
                .andExpect(jsonPath("$.error.code").value("CAR_NOT_FOUND"));
    }

    @Test
    @DisplayName("차량을 삭제하면 204다")
    void deleteCar() throws Exception {
        mockMvc.perform(delete(CARS + "/3").with(member()))
                .andExpect(status().isNoContent());
    }

    @Test
    @DisplayName("내 차량이 아니면 삭제는 404 CAR_NOT_FOUND다")
    void deleteNotMine() throws Exception {
        willThrow(new BusinessException(UserErrorCode.CAR_NOT_FOUND)).given(ownedCarService).delete(42L, 9L);

        mockMvc.perform(delete(CARS + "/9").with(member()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("CAR_NOT_FOUND"));
    }

    @Test
    @DisplayName("내 차량을 id, 차종, 번호로 목록에 담아 내린다")
    void findAll() throws Exception {
        given(ownedCarService.findAll(42L)).willReturn(List.of(
                new MyCar(1L, "아반떼", "12가3456", CREATED_AT, CREATED_AT)));

        mockMvc.perform(get(CARS).with(member()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("조회에 성공했습니다"))
                .andExpect(jsonPath("$.data.cars.length()").value(1))
                .andExpect(jsonPath("$.data.cars[0].id").value(1))
                .andExpect(jsonPath("$.data.cars[0].model").value("아반떼"))
                .andExpect(jsonPath("$.data.cars[0].number").value("12가3456"))
                .andExpect(jsonPath("$.data.cars[0].created_at").doesNotExist());
    }

    @Test
    @DisplayName("등록한 차량이 없으면 200과 빈 배열이다")
    void none() throws Exception {
        given(ownedCarService.findAll(42L)).willReturn(List.of());

        mockMvc.perform(get(CARS).with(member()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("등록된 차량이 없습니다"))
                .andExpect(jsonPath("$.data.cars").isEmpty());
    }

    @Test
    @DisplayName("액세스 토큰이 없으면 401이다")
    void anonymous() throws Exception {
        mockMvc.perform(get(CARS))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("UNAUTHORIZED"));

        verifyNoInteractions(ownedCarService);
    }

    private static RequestPostProcessor member() {
        return jwt().jwt(builder -> builder.subject("42")).authorities(Authority.USER);
    }
}
