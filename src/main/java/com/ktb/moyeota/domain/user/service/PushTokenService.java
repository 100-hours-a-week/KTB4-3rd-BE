package com.ktb.moyeota.domain.user.service;

import com.ktb.moyeota.domain.user.dto.PushTokenRequest;
import com.ktb.moyeota.domain.user.error.PushTokenErrorCode;
import com.ktb.moyeota.domain.user.repository.PushTokenRepository;
import com.ktb.moyeota.global.exception.BusinessException;
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
        validate(request, true);
        pushTokenRepository.upsert(userId, request.token(), LocalDateTime.now(clock).truncatedTo(ChronoUnit.MICROS));
    }

    @Transactional
    public void delete(Long userId, PushTokenRequest request) {
        validate(request, false);
        pushTokenRepository.deleteByUserIdAndToken(userId, request.token());
    }

    private void validate(PushTokenRequest request, boolean checkLength) {
        if (request.isBlank() || (checkLength && request.exceedsMaxLength())) {
            throw new BusinessException(PushTokenErrorCode.INVALID_PUSH_TOKEN);
        }
    }
}
