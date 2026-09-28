package com.ktb.moyeota.global.security.signup;

import com.ktb.moyeota.domain.auth.service.OpaqueTokenFactory;
import com.ktb.moyeota.domain.auth.store.SignupSessionStore;
import com.ktb.moyeota.global.security.cookie.AuthCookies;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Clock;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;
import org.springframework.web.util.WebUtils;

@Component
@RequiredArgsConstructor
public class SignupSessionAuthenticator {

    private final SignupSessionStore signupSessionStore;
    private final OpaqueTokenFactory opaqueTokenFactory;
    private final Clock clock;

    public Authentication authenticate(HttpServletRequest request) {
        Cookie cookie = WebUtils.getCookie(request, AuthCookies.SIGNUP_TOKEN);
        if (cookie == null || cookie.getValue().isBlank()) {
            return null;
        }
        LocalDateTime now = LocalDateTime.now(clock);
        return signupSessionStore.find(opaqueTokenFactory.hash(cookie.getValue()))
                .filter(session -> !session.isExpiredAt(now))
                .map(SignupAuthentication::new)
                .orElse(null);
    }
}
