package com.ktb.moyeota.domain.auth.store;

import com.ktb.moyeota.domain.auth.model.OAuthUserProfile;
import com.ktb.moyeota.domain.auth.model.SignupSessionView;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

public class InMemorySignupSessionStore implements SignupSessionStore {

    private final Map<String, SignupSessionView> sessions = new ConcurrentHashMap<>();

    @Override
    public void create(String tokenHash, OAuthUserProfile profile, LocalDateTime expiresAt) {
        sessions.put(tokenHash, new SignupSessionView(
                tokenHash, profile.provider(), profile.providerUserId(), profile.name(), expiresAt));
    }

    @Override
    public Optional<SignupSessionView> find(String tokenHash) {
        return Optional.ofNullable(sessions.get(tokenHash));
    }

    @Override
    public void delete(String tokenHash) {
        sessions.remove(tokenHash);
    }
}
