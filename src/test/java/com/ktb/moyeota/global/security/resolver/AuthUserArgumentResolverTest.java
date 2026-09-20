package com.ktb.moyeota.global.security.resolver;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ktb.moyeota.global.exception.BusinessException;
import com.ktb.moyeota.global.exception.CommonErrorCode;
import java.lang.reflect.Method;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.MethodParameter;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

class AuthUserArgumentResolverTest {

    private final AuthUserArgumentResolver resolver = new AuthUserArgumentResolver();

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("@AuthUser가 붙은 Long 파라미터만 처리한다")
    void supportsOnlyAnnotatedLong() throws Exception {
        assertThat(resolver.supportsParameter(parameterOf("annotatedLong", Long.class))).isTrue();
        assertThat(resolver.supportsParameter(parameterOf("plainLong", Long.class))).isFalse();
        assertThat(resolver.supportsParameter(parameterOf("annotatedString", String.class)))
                .isFalse();
    }

    @Test
    @DisplayName("JWT의 sub를 Long으로 꺼낸다")
    void resolvesSubjectAsUserId() throws Exception {
        authenticateWithJwt("4242");

        assertThat(resolver.resolveArgument(parameterOf("annotatedLong", Long.class), null, null, null))
                .isEqualTo(4242L);
    }

    @Test
    @DisplayName("JWT 인증이 아니면 UNAUTHORIZED를 던진다")
    void rejectsNonJwtAuthentication() throws Exception {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("someone", null, List.of()));

        assertUnauthorized();
    }

    @Test
    @DisplayName("인증이 없으면 UNAUTHORIZED를 던진다")
    void rejectsMissingAuthentication() {
        assertUnauthorized();
    }

    private void assertUnauthorized() {
        assertThatThrownBy(() ->
                resolver.resolveArgument(parameterOf("annotatedLong", Long.class), null, null, null))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(CommonErrorCode.UNAUTHORIZED);
    }

    private void authenticateWithJwt(String subject) {
        Jwt jwt = Jwt.withTokenValue("token")
                .header("alg", "HS256")
                .subject(subject)
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(1800))
                .build();
        Authentication authentication = new JwtAuthenticationToken(
                jwt, List.of(new SimpleGrantedAuthority("USER")));
        SecurityContextHolder.getContext().setAuthentication(authentication);
    }

    private MethodParameter parameterOf(String methodName, Class<?> parameterType) throws Exception {
        Method method = Probe.class.getDeclaredMethod(methodName, parameterType);
        return new MethodParameter(method, 0);
    }

    @SuppressWarnings("unused")
    static class Probe {

        void annotatedLong(@AuthUser Long userId) {
        }

        void plainLong(Long userId) {
        }

        void annotatedString(@AuthUser String userId) {
        }
    }
}
