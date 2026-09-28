package com.ktb.moyeota.domain.auth.service;

import com.ktb.moyeota.domain.auth.error.OAuthLoginError;
import com.ktb.moyeota.domain.auth.error.OAuthLoginException;
import com.ktb.moyeota.domain.auth.model.AuthorizeRedirect;
import com.ktb.moyeota.domain.auth.model.CallbackParams;
import com.ktb.moyeota.domain.auth.model.IssuedSession;
import com.ktb.moyeota.domain.auth.model.OAuthCallbackResult;
import com.ktb.moyeota.domain.auth.model.OAuthFront;
import com.ktb.moyeota.domain.auth.model.OAuthProvider;
import com.ktb.moyeota.domain.auth.model.OAuthUserProfile;
import com.ktb.moyeota.domain.auth.repository.OAuthAccountRepository;
import com.ktb.moyeota.domain.auth.store.SignupSessionStore;
import com.ktb.moyeota.global.external.kakao.KakaoOAuthClient;
import com.ktb.moyeota.global.security.AuthProperties;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class OAuthLoginService {

    private static final String ACCESS_DENIED = "access_denied";

    private final KakaoOAuthClient kakaoOAuthClient;
    private final OpaqueTokenFactory opaqueTokenFactory;
    private final OAuthAccountRepository oAuthAccountRepository;
    private final SignupSessionStore signupSessionStore;
    private final AuthSessionService authSessionService;
    private final AuthProperties authProperties;
    private final Clock clock;

    public AuthorizeRedirect buildAuthorizeRedirect(OAuthProvider provider, OAuthFront front) {
        String state = opaqueTokenFactory.generate();
        URI location = switch (provider) {
            case KAKAO -> kakaoOAuthClient.buildAuthorizeUri(state, front.redirectUri());
        };
        return new AuthorizeRedirect(location, state);
    }

    public OAuthCallbackResult handleCallback(
            OAuthProvider provider, CallbackParams params, OAuthFront front) {
        if (!stateMatches(params.stateCookie(), params.state())) {
            return new OAuthCallbackResult.Failed(OAuthLoginError.INVALID_STATE);
        }
        if (ACCESS_DENIED.equals(params.error())) {
            return new OAuthCallbackResult.Failed(OAuthLoginError.CONSENT_DENIED);
        }
        if (params.code() == null || params.code().isBlank()) {
            return new OAuthCallbackResult.Failed(OAuthLoginError.INVALID_OAUTH_CODE);
        }

        OAuthUserProfile profile;
        try {
            profile = fetchProfile(provider, params.code(), front.redirectUri());
        } catch (OAuthLoginException e) {
            return new OAuthCallbackResult.Failed(e.getError());
        }

        return oAuthAccountRepository.findActiveUserId(profile.provider(), profile.providerUserId())
                .map(this::login)
                .orElseGet(() -> openSignupSession(profile));
    }

    private OAuthUserProfile fetchProfile(OAuthProvider provider, String code, String redirectUri) {
        return switch (provider) {
            case KAKAO -> kakaoOAuthClient.fetchProfile(code, redirectUri);
        };
    }

    private OAuthCallbackResult login(Long userId) {
        IssuedSession issued = authSessionService.issue(userId);
        return new OAuthCallbackResult.Existing(issued.refreshToken(), issued.refreshTokenMaxAge());
    }

    private OAuthCallbackResult openSignupSession(OAuthUserProfile profile) {
        String signupToken = opaqueTokenFactory.generate();
        Duration ttl = authProperties.signup().ttl();
        signupSessionStore.create(
                opaqueTokenFactory.hash(signupToken), profile, LocalDateTime.now(clock).plus(ttl));
        return new OAuthCallbackResult.SignupRequired(signupToken, ttl);
    }

    private boolean stateMatches(String cookie, String query) {
        if (cookie == null || cookie.isBlank() || query == null || query.isBlank()) {
            return false;
        }
        return MessageDigest.isEqual(
                cookie.getBytes(StandardCharsets.UTF_8), query.getBytes(StandardCharsets.UTF_8));
    }
}
