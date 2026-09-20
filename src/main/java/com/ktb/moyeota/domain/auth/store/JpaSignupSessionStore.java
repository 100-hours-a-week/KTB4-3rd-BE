package com.ktb.moyeota.domain.auth.store;

import com.ktb.moyeota.domain.auth.entity.SignupSession;
import com.ktb.moyeota.domain.auth.model.OAuthUserProfile;
import com.ktb.moyeota.domain.auth.repository.SignupSessionRepository;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class JpaSignupSessionStore implements SignupSessionStore {

    private final SignupSessionRepository signupSessionRepository;

    @Override
    @Transactional
    public void create(String tokenHash, OAuthUserProfile profile, LocalDateTime expiresAt) {
        signupSessionRepository.save(SignupSession.open(tokenHash, profile, expiresAt));
    }
}
