package com.ktb.moyeota.domain.auth.store;

import com.ktb.moyeota.domain.auth.entity.Session;
import com.ktb.moyeota.domain.auth.entity.SessionToken;
import com.ktb.moyeota.domain.auth.model.SessionTokenView;
import com.ktb.moyeota.domain.auth.repository.SessionRepository;
import com.ktb.moyeota.domain.auth.repository.SessionTokenRepository;
import java.time.LocalDateTime;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class JpaSessionStore implements SessionStore {

    private final SessionRepository sessionRepository;
    private final SessionTokenRepository sessionTokenRepository;

    @Override
    @Transactional
    public Long create(Long userId, String tokenHash, LocalDateTime absoluteExpiresAt) {
        Session session = sessionRepository.save(Session.open(userId, absoluteExpiresAt));
        sessionTokenRepository.save(SessionToken.issue(tokenHash, session));
        return session.getId();
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<SessionTokenView> findToken(String tokenHash) {
        return sessionTokenRepository.findViewByTokenHash(tokenHash);
    }

    @Override
    @Transactional
    public boolean markSuperseded(String tokenHash, LocalDateTime now) {
        return sessionTokenRepository.markSuperseded(tokenHash, now) == 1;
    }

    @Override
    @Transactional
    public void appendToken(Long sessionId, String tokenHash) {
        sessionTokenRepository.save(
                SessionToken.issue(tokenHash, sessionRepository.getReferenceById(sessionId)));
    }

    @Override
    @Transactional
    public void deleteSession(Long sessionId) {
        sessionTokenRepository.deleteBySessionId(sessionId);
        sessionRepository.deleteById(sessionId);
    }
}
