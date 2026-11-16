package com.ktb.moyeota.domain.auth.service;

import com.ktb.moyeota.domain.auth.repository.SignupSessionRepository;
import java.time.Clock;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ExpiredSignupSessionCleanupService {

    private final SignupSessionRepository signupSessionRepository;
    private final Clock clock;

    @Transactional
    public int deleteExpired() {
        return signupSessionRepository.deleteExpired(LocalDateTime.now(clock));
    }
}
