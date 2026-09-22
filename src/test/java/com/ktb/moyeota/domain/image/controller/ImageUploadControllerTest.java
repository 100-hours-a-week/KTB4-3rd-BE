package com.ktb.moyeota.domain.image.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ktb.moyeota.domain.auth.model.OAuthProvider;
import com.ktb.moyeota.domain.auth.model.OAuthUserProfile;
import com.ktb.moyeota.domain.auth.service.OpaqueTokenFactory;
import com.ktb.moyeota.domain.auth.store.InMemorySignupSessionStore;
import com.ktb.moyeota.domain.auth.store.SignupSessionStore;
import com.ktb.moyeota.domain.image.model.ImagePurpose;
import com.ktb.moyeota.domain.image.model.ImageType;
import com.ktb.moyeota.domain.image.model.IssuedUploadUrl;
import com.ktb.moyeota.domain.image.model.PresignedUrlCommand;
import com.ktb.moyeota.domain.image.model.UploadScope;
import com.ktb.moyeota.domain.image.service.ImageUploadService;
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
import jakarta.servlet.http.Cookie;
import java.net.URI;
import java.time.LocalDateTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

@WebMvcTest(controllers = ImageUploadController.class)
@Import({SecurityConfig.class, CorsConfig.class, JwtConfig.class, ClockConfig.class, WebConfig.class,
        AuthUserArgumentResolver.class, SignupPrincipalArgumentResolver.class, UploadScopeArgumentResolver.class,
        ApiAuthenticationEntryPoint.class, ApiAccessDeniedHandler.class, GlobalExceptionHandler.class,
        SignupSessionAuthenticator.class, OpaqueTokenFactory.class, ImageUploadControllerTest.StoreConfig.class})
@EnableConfigurationProperties({AuthProperties.class, CorsProperties.class})
class ImageUploadControllerTest {

    private static final String SIGNUP_TOKEN = "signup_token";
    private static final String VALID_BODY = """
            {"purpose":"PROFILE","content_type":"image/jpeg","content_length":482113}
            """;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private SignupSessionStore signupSessionStore;

    @Autowired
    private OpaqueTokenFactory opaqueTokenFactory;

    @MockitoBean
    private ImageUploadService imageUploadService;

    @Nested
    @DisplayName("호출 자격")
    class Credentials {

        @Test
        @DisplayName("회원가입 쿠키로 호출하면 회원가입 세션 scope로 발급한다")
        void signupSession() throws Exception {
            givenIssued();
            Cookie cookie = validSignupCookie();

            mockMvc.perform(issue(VALID_BODY).cookie(cookie))
                    .andExpect(status().isOk());

            assertThat(capturedScope())
                    .isEqualTo(UploadScope.signup(opaqueTokenFactory.hash(cookie.getValue())));
        }

        @Test
        @DisplayName("회원의 액세스 토큰으로 호출하면 회원 scope로 발급한다")
        void memberAccessToken() throws Exception {
            givenIssued();

            mockMvc.perform(issue(VALID_BODY).with(member(42L)))
                    .andExpect(status().isOk());

            assertThat(capturedScope()).isEqualTo(UploadScope.user(42L));
        }

        @Test
        @DisplayName("아무 자격도 없으면 401이다")
        void anonymous() throws Exception {
            mockMvc.perform(issue(VALID_BODY))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.error.code").value("UNAUTHORIZED"));

            verifyNoInteractions(imageUploadService);
        }

        @Test
        @DisplayName("만료된 회원가입 세션이면 401이다")
        void expiredSession() throws Exception {
            mockMvc.perform(issue(VALID_BODY).cookie(signupCookie(LocalDateTime.now().minusSeconds(1))))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("허용되지 않는 용도를 요청하면 403이다")
        void forbiddenPurpose() throws Exception {
            given(imageUploadService.issue(any(), any()))
                    .willThrow(new BusinessException(CommonErrorCode.FORBIDDEN));

            mockMvc.perform(issue(VALID_BODY).cookie(validSignupCookie()))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.error.code").value("FORBIDDEN"));
        }
    }

    @Nested
    @DisplayName("응답")
    class Response {

        @Test
        @DisplayName("업로드 URL과 키, 만료 초를 돌려준다")
        void respondsIssuedUrl() throws Exception {
            givenIssued();

            mockMvc.perform(issue(VALID_BODY).with(member(42L)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.message").value("이미지 업로드 URL이 발급되었습니다."))
                    .andExpect(jsonPath("$.data.upload_url")
                            .value("https://bucket.s3.ap-northeast-2.amazonaws.com/tmp/profile/u-42/a.jpg?X-Amz-Expires=300"))
                    .andExpect(jsonPath("$.data.image_key").value("tmp/profile/u-42/a.jpg"))
                    .andExpect(jsonPath("$.data.expires_in").value(300));
        }

        @Test
        @DisplayName("본문을 커맨드로 바꿔 서비스에 넘긴다")
        void passesCommand() throws Exception {
            givenIssued();

            mockMvc.perform(issue("""
                    {"purpose":"PROFILE","content_type":"image/webp","content_length":100}
                    """).with(member(42L)));

            ArgumentCaptor<PresignedUrlCommand> command = ArgumentCaptor.forClass(PresignedUrlCommand.class);
            verify(imageUploadService).issue(any(), command.capture());
            assertThat(command.getValue())
                    .isEqualTo(new PresignedUrlCommand(ImagePurpose.PROFILE, ImageType.WEBP, 100L));
        }
    }

    @Nested
    @DisplayName("요청 검증")
    class Validation {

        @Test
        @DisplayName("지원하지 않는 용도는 422 INVALID_ENUM이다")
        void unsupportedPurpose() throws Exception {
            mockMvc.perform(issue("""
                    {"purpose":"POST","content_type":"image/jpeg","content_length":100}
                    """).with(member(42L)))
                    .andExpect(status().isUnprocessableContent())
                    .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"))
                    .andExpect(jsonPath("$.error.field").value("purpose"))
                    .andExpect(jsonPath("$.error.details[0].reason").value("INVALID_ENUM"));

            verifyNoInteractions(imageUploadService);
        }

        @Test
        @DisplayName("이미지가 아닌 타입은 422 INVALID_ENUM이다")
        void unsupportedContentType() throws Exception {
            mockMvc.perform(issue("""
                    {"purpose":"PROFILE","content_type":"image/gif","content_length":100}
                    """).with(member(42L)))
                    .andExpect(status().isUnprocessableContent())
                    .andExpect(jsonPath("$.error.field").value("content_type"))
                    .andExpect(jsonPath("$.error.details[0].reason").value("INVALID_ENUM"));
        }

        @Test
        @DisplayName("5MB를 넘으면 422 OUT_OF_RANGE다")
        void tooLarge() throws Exception {
            mockMvc.perform(issue("""
                    {"purpose":"PROFILE","content_type":"image/jpeg","content_length":5242881}
                    """).with(member(42L)))
                    .andExpect(status().isUnprocessableContent())
                    .andExpect(jsonPath("$.error.field").value("content_length"))
                    .andExpect(jsonPath("$.error.details[0].reason").value("OUT_OF_RANGE"));
        }

        @Test
        @DisplayName("필드를 빼면 422 REQUIRED다")
        void missingFields() throws Exception {
            mockMvc.perform(issue("""
                    {"purpose":"PROFILE"}
                    """).with(member(42L)))
                    .andExpect(status().isUnprocessableContent())
                    .andExpect(jsonPath("$.error.details[0].field").value("content_type"))
                    .andExpect(jsonPath("$.error.details[0].reason").value("REQUIRED"))
                    .andExpect(jsonPath("$.error.details[1].field").value("content_length"))
                    .andExpect(jsonPath("$.error.details[1].reason").value("REQUIRED"));
        }

        @Test
        @DisplayName("JSON이 깨지면 400 MALFORMED_REQUEST다")
        void malformedJson() throws Exception {
            mockMvc.perform(issue("{\"purpose\": ").with(member(42L)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.error.code").value("MALFORMED_REQUEST"));
        }
    }

    private void givenIssued() throws Exception {
        given(imageUploadService.issue(any(), any())).willReturn(new IssuedUploadUrl(
                URI.create("https://bucket.s3.ap-northeast-2.amazonaws.com/tmp/profile/u-42/a.jpg?X-Amz-Expires=300")
                        .toURL(),
                "tmp/profile/u-42/a.jpg",
                300));
    }

    private UploadScope capturedScope() {
        ArgumentCaptor<UploadScope> scope = ArgumentCaptor.forClass(UploadScope.class);
        verify(imageUploadService).issue(scope.capture(), any());
        return scope.getValue();
    }

    private static MockHttpServletRequestBuilder issue(String body) {
        return post("/images/presigned-url").contentType(MediaType.APPLICATION_JSON).content(body);
    }

    private static RequestPostProcessor member(Long userId) {
        return jwt().jwt(builder -> builder.subject(String.valueOf(userId))).authorities(Authority.USER);
    }

    private Cookie validSignupCookie() {
        return signupCookie(LocalDateTime.now().plusMinutes(15));
    }

    private Cookie signupCookie(LocalDateTime expiresAt) {
        String token = opaqueTokenFactory.generate();
        signupSessionStore.create(opaqueTokenFactory.hash(token),
                new OAuthUserProfile(OAuthProvider.KAKAO, "1234567890", "카카오닉네임"), expiresAt);
        return new Cookie(SIGNUP_TOKEN, token);
    }

    @TestConfiguration
    static class StoreConfig {

        @Bean
        SignupSessionStore signupSessionStore() {
            return new InMemorySignupSessionStore();
        }
    }
}
