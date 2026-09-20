package com.ktb.moyeota.domain.auth.store;

import com.ktb.moyeota.domain.auth.model.OAuthUserProfile;
import java.time.LocalDateTime;

public interface SignupSessionStore {

    void create(String tokenHash, OAuthUserProfile profile, LocalDateTime expiresAt);
}
