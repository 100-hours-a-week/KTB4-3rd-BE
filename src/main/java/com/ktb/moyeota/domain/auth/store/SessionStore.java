package com.ktb.moyeota.domain.auth.store;

import com.ktb.moyeota.domain.auth.model.SessionTokenView;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.function.Supplier;

public interface SessionStore {

    <T> T withinStoreTransaction(Supplier<T> operations);

    Long create(Long userId, String tokenHash, LocalDateTime absoluteExpiresAt);

    Optional<SessionTokenView> findToken(String tokenHash);

    boolean rotate(Long sessionId, String tokenHash, String newTokenHash, LocalDateTime now);

    void deleteSession(Long sessionId);

    void deleteAllByUserId(Long userId);
}
