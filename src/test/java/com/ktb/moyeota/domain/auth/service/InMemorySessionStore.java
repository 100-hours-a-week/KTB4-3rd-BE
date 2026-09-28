package com.ktb.moyeota.domain.auth.service;

import com.ktb.moyeota.domain.auth.model.SessionTokenView;
import com.ktb.moyeota.domain.auth.store.SessionStore;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

public class InMemorySessionStore implements SessionStore {

    private record SessionRow(Long userId, LocalDateTime absoluteExpiresAt) {
    }

    private record TokenRow(Long sessionId, LocalDateTime supersededAt) {
    }

    private final AtomicLong sequence = new AtomicLong();
    private final Map<Long, SessionRow> sessions = new ConcurrentHashMap<>();
    private final Map<String, TokenRow> tokens = new ConcurrentHashMap<>();

    @Override
    public Long create(Long userId, String tokenHash, LocalDateTime absoluteExpiresAt) {
        Long sessionId = sequence.incrementAndGet();
        sessions.put(sessionId, new SessionRow(userId, absoluteExpiresAt));
        tokens.put(tokenHash, new TokenRow(sessionId, null));
        return sessionId;
    }

    @Override
    public Optional<SessionTokenView> findToken(String tokenHash) {
        TokenRow token = tokens.get(tokenHash);
        if (token == null) {
            return Optional.empty();
        }
        SessionRow session = sessions.get(token.sessionId());
        if (session == null) {
            return Optional.empty();
        }
        return Optional.of(new SessionTokenView(
                tokenHash, token.sessionId(), session.userId(),
                token.supersededAt(), session.absoluteExpiresAt()));
    }

    @Override
    public boolean markSuperseded(String tokenHash, LocalDateTime now) {
        TokenRow updated = tokens.computeIfPresent(tokenHash,
                (key, row) -> row.supersededAt() == null ? new TokenRow(row.sessionId(), now) : row);
        return updated != null && now.equals(updated.supersededAt());
    }

    @Override
    public void appendToken(Long sessionId, String tokenHash) {
        tokens.put(tokenHash, new TokenRow(sessionId, null));
    }

    @Override
    public void deleteSession(Long sessionId) {
        sessions.remove(sessionId);
        tokens.entrySet().removeIf(entry -> entry.getValue().sessionId().equals(sessionId));
    }
}
