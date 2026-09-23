package com.ktb.moyeota.domain.auth.store;

import com.ktb.moyeota.domain.auth.model.OAuthUserProfile;
import com.ktb.moyeota.domain.auth.model.SignupSessionView;
import java.time.LocalDateTime;
import java.util.Optional;

public interface SignupSessionStore {

    void create(String tokenHash, OAuthUserProfile profile, LocalDateTime expiresAt);

    Optional<SignupSessionView> find(String tokenHash);

    void delete(String tokenHash);
}
