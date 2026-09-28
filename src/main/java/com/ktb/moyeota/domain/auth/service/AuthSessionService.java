package com.ktb.moyeota.domain.auth.service;

import com.ktb.moyeota.domain.auth.model.IssuedSession;
import com.ktb.moyeota.domain.auth.model.ReissueResult;
import com.ktb.moyeota.domain.auth.model.SessionTokenView;
import com.ktb.moyeota.domain.auth.store.SessionStore;
import com.ktb.moyeota.global.security.AuthProperties;
import com.ktb.moyeota.global.security.jwt.AccessTokenProvider;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthSessionService {

    private final SessionStore sessionStore;
    private final OpaqueTokenFactory opaqueTokenFactory;
    private final AccessTokenProvider accessTokenProvider;
    private final AuthProperties authProperties;
    private final Clock clock;

    @Transactional
    public IssuedSession issue(Long userId) {
        String refreshToken = opaqueTokenFactory.generate();
        Duration ttl = authProperties.refresh().ttl();
        sessionStore.create(userId, opaqueTokenFactory.hash(refreshToken), LocalDateTime.now(clock).plus(ttl));
        return new IssuedSession(accessTokenProvider.issue(userId), refreshToken, ttl);
    }

    @Transactional
    public ReissueResult reissue(String refreshToken) {
        if (refreshToken == null || refreshToken.isBlank()) {
            return new ReissueResult.Rejected();
        }

        Optional<SessionTokenView> found = sessionStore.findToken(opaqueTokenFactory.hash(refreshToken));
        if (found.isEmpty()) {
            return new ReissueResult.Rejected();
        }

        SessionTokenView view = found.get();
        LocalDateTime now = LocalDateTime.now(clock);

        if (view.isExpiredAt(now)) {
            sessionStore.deleteSession(view.sessionId());
            return new ReissueResult.Rejected();
        }
        if (view.isSuperseded()) {
            return graceOrReuse(view, now);
        }
        if (!sessionStore.markSuperseded(view.tokenHash(), now)) {
            return new ReissueResult.Graced(accessTokenProvider.issue(view.userId()));
        }
        return rotate(view, now);
    }

    @Transactional
    public void revokeSession(String refreshToken) {
        if (refreshToken == null || refreshToken.isBlank()) {
            return;
        }
        sessionStore.findToken(opaqueTokenFactory.hash(refreshToken))
                .ifPresent(view -> sessionStore.deleteSession(view.sessionId()));
    }

    private ReissueResult rotate(SessionTokenView view, LocalDateTime now) {
        String rotated = opaqueTokenFactory.generate();
        sessionStore.appendToken(view.sessionId(), opaqueTokenFactory.hash(rotated));
        return new ReissueResult.Rotated(
                accessTokenProvider.issue(view.userId()),
                rotated,
                Duration.between(now, view.absoluteExpiresAt()));
    }

    private ReissueResult graceOrReuse(SessionTokenView view, LocalDateTime now) {
        Duration sinceSuperseded = Duration.between(view.supersededAt(), now);
        if (sinceSuperseded.compareTo(authProperties.refresh().grace()) <= 0) {
            return new ReissueResult.Graced(accessTokenProvider.issue(view.userId()));
        }
        log.warn("[REFRESH_TOKEN_REUSE] userId={} sessionId={} supersededAt={} now={}",
                view.userId(), view.sessionId(), view.supersededAt(), now);
        sessionStore.deleteSession(view.sessionId());
        return new ReissueResult.Rejected();
    }
}
