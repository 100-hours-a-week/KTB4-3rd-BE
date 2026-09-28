package com.ktb.moyeota.domain.user.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.ktb.moyeota.domain.auth.model.IssuedSession;
import com.ktb.moyeota.domain.auth.model.OAuthProvider;
import com.ktb.moyeota.domain.auth.model.OAuthUserProfile;
import com.ktb.moyeota.domain.auth.service.OpaqueTokenFactory;
import com.ktb.moyeota.domain.auth.store.InMemorySignupSessionStore;
import com.ktb.moyeota.domain.auth.store.SignupSessionStore;
import com.ktb.moyeota.domain.user.model.RegisteredUser;
import com.ktb.moyeota.domain.user.service.UserService;
import com.ktb.moyeota.global.config.ClockConfig;
import com.ktb.moyeota.global.config.CorsConfig;
import com.ktb.moyeota.global.config.CorsProperties;
import com.ktb.moyeota.global.exception.GlobalExceptionHandler;
import com.ktb.moyeota.global.security.AuthProperties;
import com.ktb.moyeota.global.security.SecurityConfig;
import com.ktb.moyeota.global.security.cookie.AuthCookies;
import com.ktb.moyeota.global.security.handler.ApiAccessDeniedHandler;
import com.ktb.moyeota.global.security.handler.ApiAuthenticationEntryPoint;
import com.ktb.moyeota.global.security.jwt.AccessToken;
import com.ktb.moyeota.global.security.jwt.JwtConfig;
import com.ktb.moyeota.global.security.signup.SignupSessionAuthenticator;
import jakarta.servlet.http.Cookie;
import java.time.Duration;
import java.time.LocalDateTime;
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

@WebMvcTest(controllers = UserController.class)
@Import({SecurityConfig.class, CorsConfig.class, JwtConfig.class, ClockConfig.class,
        AuthCookies.class, ApiAuthenticationEntryPoint.class, ApiAccessDeniedHandler.class,
        GlobalExceptionHandler.class, SignupSessionAuthenticator.class,
        OpaqueTokenFactory.class, SignupApiTestSupport.StoreConfig.class})
@EnableConfigurationProperties({AuthProperties.class, CorsProperties.class})
abstract class SignupApiTestSupport {

    protected static final String SIGNUP_TOKEN = "signup_token";
    protected static final String REFRESH_TOKEN = "refresh_token";
    protected static final String ALL_AGREED = """
            {"service":true,"location":true,"gender":true,"account_third_party":true,"marketing":false}""";
    protected static final String VALID_BODY = """
            {"nickname":"길동이","gender":"FEMALE","agreements":%s}
            """.formatted(ALL_AGREED);
    protected static final LocalDateTime CREATED_AT = LocalDateTime.of(2026, 9, 6, 9, 0);

    @Autowired
    protected MockMvc mockMvc;

    @Autowired
    protected SignupSessionStore signupSessionStore;

    @Autowired
    protected OpaqueTokenFactory opaqueTokenFactory;

    @MockitoBean
    protected UserService userService;

    protected void givenRegistered() {
        given(userService.register(any(), any())).willReturn(new RegisteredUser(7L, null, CREATED_AT,
                new IssuedSession(new AccessToken("access-value", 1800), "refresh-value", Duration.ofDays(7))));
    }

    protected MockHttpServletRequestBuilder signUp(String body) {
        return post("/api/users").contentType(MediaType.APPLICATION_JSON).content(body);
    }

    protected Cookie validSignupCookie() {
        return signupCookie(LocalDateTime.now().plusMinutes(15));
    }

    protected Cookie signupCookie(LocalDateTime expiresAt) {
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
