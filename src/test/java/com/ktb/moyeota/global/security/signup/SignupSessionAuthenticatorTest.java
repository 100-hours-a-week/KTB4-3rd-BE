package com.ktb.moyeota.global.security.signup;

import static org.assertj.core.api.Assertions.assertThat;

import com.ktb.moyeota.domain.auth.model.OAuthProvider;
import com.ktb.moyeota.domain.auth.model.OAuthUserProfile;
import com.ktb.moyeota.domain.auth.model.SignupSessionView;
import com.ktb.moyeota.domain.auth.service.OpaqueTokenFactory;
import com.ktb.moyeota.domain.auth.store.InMemorySignupSessionStore;
import com.ktb.moyeota.domain.auth.store.SignupSessionStore;
import com.ktb.moyeota.global.security.Authority;
import jakarta.servlet.http.Cookie;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;

class SignupSessionAuthenticatorTest {

    private static final Instant NOW = Instant.parse("2026-01-01T00:00:00Z");
    private static final LocalDateTime NOW_LOCAL = LocalDateTime.ofInstant(NOW, ZoneOffset.UTC);
    private static final OAuthUserProfile PROFILE = new OAuthUserProfile(OAuthProvider.KAKAO, "1234567890");

    private final SignupSessionStore store = new InMemorySignupSessionStore();
    private final OpaqueTokenFactory opaqueTokenFactory = new OpaqueTokenFactory();
    private final SignupSessionAuthenticator authenticator = new SignupSessionAuthenticator(
            store, opaqueTokenFactory, Clock.fixed(NOW, ZoneOffset.UTC));

    @Test
    @DisplayName("유효한 쿠키면 SIGNUP 권한의 인증을 만들고 회원가입 세션을 주체로 싣는다")
    void validCookieBecomesSignupAuthentication() {
        String token = openSession(NOW_LOCAL.plusMinutes(15));

        Authentication authentication = authenticator.authenticate(requestWith(token));

        assertThat(authentication).isInstanceOf(SignupAuthentication.class);
        assertThat(authentication.isAuthenticated()).isTrue();
        assertThat(authentication.getAuthorities()).extracting(GrantedAuthority::getAuthority)
                .containsExactly(Authority.SIGNUP_NAME);
        SignupSessionView principal = (SignupSessionView) authentication.getPrincipal();
        assertThat(principal.providerUserId()).isEqualTo("1234567890");
        assertThat(principal.tokenHash()).isEqualTo(opaqueTokenFactory.hash(token));
    }

    @Test
    @DisplayName("쿠키가 없으면 인증을 만들지 않는다")
    void noCookie() {
        assertThat(authenticator.authenticate(new MockHttpServletRequest())).isNull();
    }

    @Test
    @DisplayName("쿠키 값이 비어 있으면 인증을 만들지 않는다")
    void blankCookie() {
        assertThat(authenticator.authenticate(requestWith(" "))).isNull();
    }

    @Test
    @DisplayName("저장소에 없는 토큰이면 인증을 만들지 않는다")
    void unknownToken() {
        assertThat(authenticator.authenticate(requestWith("never-issued"))).isNull();
    }

    @Test
    @DisplayName("만료 시각이 된 세션이면 인증을 만들지 않는다")
    void expiredSession() {
        String token = openSession(NOW_LOCAL);

        assertThat(authenticator.authenticate(requestWith(token))).isNull();
    }

    @Test
    @DisplayName("쿠키 원문이 아니라 해시로 저장소를 조회한다")
    void looksUpByHash() {
        String token = opaqueTokenFactory.generate();
        store.create(token, PROFILE, NOW_LOCAL.plusMinutes(15));

        assertThat(authenticator.authenticate(requestWith(token))).isNull();
    }

    private String openSession(LocalDateTime expiresAt) {
        String token = opaqueTokenFactory.generate();
        store.create(opaqueTokenFactory.hash(token), PROFILE, expiresAt);
        return token;
    }

    private MockHttpServletRequest requestWith(String signupToken) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setCookies(new Cookie("signup_token", signupToken));
        return request;
    }
}
