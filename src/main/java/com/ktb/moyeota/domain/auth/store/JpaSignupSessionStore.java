package com.ktb.moyeota.domain.auth.store;

import com.ktb.moyeota.domain.auth.entity.SignupSession;
import com.ktb.moyeota.domain.auth.model.OAuthUserProfile;
import com.ktb.moyeota.domain.auth.model.SignupSessionView;
import com.ktb.moyeota.domain.auth.repository.SignupSessionRepository;
import java.time.LocalDateTime;
import java.util.Optional;
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

    @Override
    @Transactional(readOnly = true)
    public Optional<SignupSessionView> find(String tokenHash) {
        return signupSessionRepository.findViewByTokenHash(tokenHash);
    }

    @Override
    @Transactional
    public void delete(String tokenHash) {
        signupSessionRepository.deleteByTokenHash(tokenHash);
    }
}
