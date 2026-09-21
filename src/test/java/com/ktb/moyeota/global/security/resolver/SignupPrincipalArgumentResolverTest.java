package com.ktb.moyeota.global.security.resolver;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ktb.moyeota.domain.auth.model.OAuthProvider;
import com.ktb.moyeota.domain.auth.model.SignupSessionView;
import com.ktb.moyeota.global.exception.BusinessException;
import com.ktb.moyeota.global.exception.CommonErrorCode;
import com.ktb.moyeota.global.security.Authority;
import com.ktb.moyeota.global.security.signup.SignupAuthentication;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.MethodParameter;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

class SignupPrincipalArgumentResolverTest {

    private static final SignupSessionView SESSION = new SignupSessionView(
            "hash-a", OAuthProvider.KAKAO, "1234567890", LocalDateTime.of(2026, 1, 1, 0, 15));

    private final SignupPrincipalArgumentResolver resolver = new SignupPrincipalArgumentResolver();

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("@SignupPrincipal이 붙은 SignupSessionView 파라미터만 처리한다")
    void supportsOnlyAnnotatedView() throws Exception {
        assertThat(resolver.supportsParameter(parameterOf("annotatedView"))).isTrue();
        assertThat(resolver.supportsParameter(parameterOf("plainView"))).isFalse();
    }

    @Test
    @DisplayName("회원가입 세션 인증이면 세션을 꺼낸다")
    void resolvesSession() {
        SecurityContextHolder.getContext().setAuthentication(new SignupAuthentication(SESSION));

        assertThat(resolve()).isEqualTo(SESSION);
    }

    @Test
    @DisplayName("인증이 없거나 익명이면 UNAUTHORIZED다")
    void anonymousIsUnauthorized() {
        assertFails(CommonErrorCode.UNAUTHORIZED);

        SecurityContextHolder.getContext().setAuthentication(new AnonymousAuthenticationToken(
                "key", "anonymousUser", AuthorityUtils.createAuthorityList("ROLE_ANONYMOUS")));
        assertFails(CommonErrorCode.UNAUTHORIZED);
    }

    @Test
    @DisplayName("로그인한 회원이 회원가입 세션 자리에 들어오면 FORBIDDEN이다")
    void memberIsForbidden() {
        Jwt jwt = Jwt.withTokenValue("token").header("alg", "HS256").subject("42")
                .issuedAt(Instant.now()).expiresAt(Instant.now().plusSeconds(60)).build();
        SecurityContextHolder.getContext()
                .setAuthentication(new JwtAuthenticationToken(jwt, List.of(Authority.USER)));

        assertFails(CommonErrorCode.FORBIDDEN);
    }

    private SignupSessionView resolve() {
        return resolver.resolveArgument(null, null, null, null);
    }

    private void assertFails(CommonErrorCode expected) {
        assertThatThrownBy(this::resolve)
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(expected));
    }

    private MethodParameter parameterOf(String methodName) throws Exception {
        return new MethodParameter(Probe.class.getDeclaredMethod(methodName, SignupSessionView.class), 0);
    }

    @SuppressWarnings("unused")
    private static class Probe {

        void annotatedView(@SignupPrincipal SignupSessionView session) {
        }

        void plainView(SignupSessionView session) {
        }
    }
}
