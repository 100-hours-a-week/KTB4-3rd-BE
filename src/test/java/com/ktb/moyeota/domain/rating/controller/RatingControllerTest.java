package com.ktb.moyeota.domain.rating.controller;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ktb.moyeota.domain.rating.error.RatingErrorCode;
import com.ktb.moyeota.domain.rating.model.RatingSubmitCommand;
import com.ktb.moyeota.domain.rating.service.RatingService;
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
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

@WebMvcTest(controllers = RatingController.class)
@Import({SecurityConfig.class, CorsConfig.class, JwtConfig.class, ClockConfig.class, WebConfig.class,
        AuthUserArgumentResolver.class, SignupPrincipalArgumentResolver.class, UploadScopeArgumentResolver.class,
        ApiAuthenticationEntryPoint.class, ApiAccessDeniedHandler.class, GlobalExceptionHandler.class})
@EnableConfigurationProperties({AuthProperties.class, CorsProperties.class})
class RatingControllerTest {

    private static final String BODY = """
            {"ratings":[{"target_user_id":7,"score":5},{"target_user_id":9,"score":4}]}""";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private RatingService ratingService;

    @MockitoBean
    private SignupSessionAuthenticator signupSessionAuthenticator;

    @Test
    @DisplayName("평가를 제출하면 201과 data null을 내린다")
    void submits() throws Exception {
        mockMvc.perform(submit(BODY).with(member()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.message").value("평가가 제출되었습니다"))
                .andExpect(content().string(containsString("\"data\":null")));
    }

    @Test
    @DisplayName("요청 값을 평가 명령으로 옮긴다")
    void mapsCommand() throws Exception {
        mockMvc.perform(submit(BODY).with(member()));

        verify(ratingService).submit(42L, 30L, new RatingSubmitCommand(List.of(
                new RatingSubmitCommand.Rating(7L, 5), new RatingSubmitCommand.Rating(9L, 4))));
    }

    @ParameterizedTest(name = "{0}", quoteTextArguments = false)
    @CsvSource(delimiter = '|', value = {
            "평가 목록이 비었음 | {\"ratings\":[]}                                   | ratings              | REQUIRED",
            "평가 목록이 없음   | {}                                                 | ratings              | REQUIRED",
            "대상이 없음       | {\"ratings\":[{\"score\":5}]}                       | ratings[0].target_user_id | REQUIRED",
            "별점이 없음       | {\"ratings\":[{\"target_user_id\":7}]}              | ratings[0].score     | REQUIRED",
            "별점이 0         | {\"ratings\":[{\"target_user_id\":7,\"score\":0}]}  | ratings[0].score     | OUT_OF_RANGE",
            "별점이 6         | {\"ratings\":[{\"target_user_id\":7,\"score\":6}]}  | ratings[0].score     | OUT_OF_RANGE"
    })
    @DisplayName("형식이 틀리면 422 VALIDATION_ERROR다")
    void validation(String caseName, String body, String field, String reason) throws Exception {
        mockMvc.perform(submit(body).with(member()))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.error.field").value(field))
                .andExpect(jsonPath("$.error.details[0].reason").value(reason));

        verifyNoInteractions(ratingService);
    }

    @Test
    @DisplayName("이미 제출했으면 409 ALREADY_RATED다")
    void alreadyRated() throws Exception {
        willThrow(new BusinessException(RatingErrorCode.ALREADY_RATED))
                .given(ratingService).submit(any(), any(), any());

        mockMvc.perform(submit(BODY).with(member()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("ALREADY_RATED"))
                .andExpect(jsonPath("$.message").value("이미 평가를 제출했습니다"));
    }

    @Test
    @DisplayName("액세스 토큰이 없으면 401이다")
    void anonymous() throws Exception {
        mockMvc.perform(submit(BODY))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(ratingService);
    }

    private static MockHttpServletRequestBuilder submit(String body) {
        return post("/api/companions/30/ratings").contentType(MediaType.APPLICATION_JSON).content(body);
    }

    private static RequestPostProcessor member() {
        return jwt().jwt(builder -> builder.subject("42")).authorities(Authority.USER);
    }
}
