package com.ktb.moyeota.domain.auth.store;

import com.ktb.moyeota.domain.auth.entity.Session;
import com.ktb.moyeota.domain.auth.entity.SessionToken;
import com.ktb.moyeota.domain.auth.model.SessionTokenView;
import com.ktb.moyeota.domain.auth.repository.SessionRepository;
import com.ktb.moyeota.domain.auth.repository.SessionTokenRepository;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.function.Supplier;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "moyeota.auth", name = "session-store", havingValue = "jpa", matchIfMissing = true)
public class JpaSessionStore implements SessionStore {

    private final SessionRepository sessionRepository;
    private final SessionTokenRepository sessionTokenRepository;

    @Override
    @Transactional
    public <T> T withinStoreTransaction(Supplier<T> operations) {
        return operations.get();
    }

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
    public boolean rotate(Long sessionId, String tokenHash, String newTokenHash, LocalDateTime now) {
        if (sessionTokenRepository.markSuperseded(tokenHash, now) != 1) {
            return false;
        }
        sessionTokenRepository.save(
                SessionToken.issue(newTokenHash, sessionRepository.getReferenceById(sessionId)));
        return true;
    }

    @Override
    @Transactional
    public void deleteSession(Long sessionId) {
        sessionTokenRepository.deleteBySessionId(sessionId);
        sessionRepository.deleteById(sessionId);
    }

    @Override
    @Transactional
    public void deleteAllByUserId(Long userId) {
        sessionTokenRepository.deleteByUserId(userId);
        sessionRepository.deleteByUserId(userId);
    }
}
