package com.ktb.moyeota.domain.user.service;

import com.ktb.moyeota.domain.user.dto.PushTokenRequest;
import com.ktb.moyeota.domain.user.repository.PushTokenRepository;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class PushTokenService {

    private final PushTokenRepository pushTokenRepository;
    private final Clock clock;

    @Transactional
    public void register(Long userId, PushTokenRequest request) {
        pushTokenRepository.upsert(userId, request.token(), LocalDateTime.now(clock).truncatedTo(ChronoUnit.MICROS));
    }

    @Transactional
    public void delete(Long userId, PushTokenRequest request) {
        pushTokenRepository.deleteByUserIdAndToken(userId, request.token());
    }
}
