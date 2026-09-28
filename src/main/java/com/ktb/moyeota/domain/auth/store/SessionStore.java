package com.ktb.moyeota.domain.auth.store;

import com.ktb.moyeota.domain.auth.model.SessionTokenView;
import java.time.LocalDateTime;
import java.util.Optional;

public interface SessionStore {

    Long create(Long userId, String tokenHash, LocalDateTime absoluteExpiresAt);

    Optional<SessionTokenView> findToken(String tokenHash);

    boolean markSuperseded(String tokenHash, LocalDateTime now);

    void appendToken(Long sessionId, String tokenHash);

    void deleteSession(Long sessionId);
}
