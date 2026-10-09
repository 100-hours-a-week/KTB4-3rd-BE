package com.ktb.moyeota.domain.carpool.service;

import com.ktb.moyeota.domain.carpool.entity.CompanionRequest;
import com.ktb.moyeota.domain.carpool.entity.CompanionRequestStatus;
import com.ktb.moyeota.domain.carpool.error.CarpoolErrorCode;
import com.ktb.moyeota.domain.carpool.model.SentJoinRequest;
import com.ktb.moyeota.domain.carpool.repository.CarpoolParticipantRepository;
import com.ktb.moyeota.domain.carpool.repository.CarpoolRepository;
import com.ktb.moyeota.domain.carpool.repository.CompanionRequestRepository;
import com.ktb.moyeota.domain.chat.entity.OutcomeStatus;
import com.ktb.moyeota.domain.companion.entity.Companion;
import com.ktb.moyeota.domain.companion.entity.CompanionStatus;
import com.ktb.moyeota.domain.user.entity.User;
import com.ktb.moyeota.domain.user.repository.UserRepository;
import com.ktb.moyeota.global.exception.BusinessException;
import com.ktb.moyeota.global.exception.CommonErrorCode;
import java.time.Clock;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class CarpoolRequestService {

    private static final int MAX_REQUESTS_PER_CARPOOL = 3;

    private final CarpoolRepository carpoolRepository;
    private final CarpoolParticipantRepository carpoolParticipantRepository;
    private final CompanionRequestRepository companionRequestRepository;
    private final UserRepository userRepository;
    private final Clock clock;

    @Transactional
    public SentJoinRequest send(Long userId, Long carpoolId, String content) {
        User requester = userRepository.findById(userId)
                .filter(user -> !user.isWithdrawn())
                .orElseThrow(() -> new BusinessException(CommonErrorCode.UNAUTHORIZED));
        Companion carpool = carpoolRepository.findCarpool(carpoolId)
                .orElseThrow(() -> new BusinessException(CarpoolErrorCode.CARPOOL_NOT_FOUND));
        validateRequestable(carpool, userId);

        CompanionRequest request = save(CompanionRequest.send(carpool, requester, content));
        return new SentJoinRequest(request.getId(), carpoolId, request.getStatus(), request.getCreatedAt());
    }

    private void validateRequestable(Companion carpool, Long userId) {
        if (carpool.isHostedBy(userId)) {
            throw new BusinessException(CarpoolErrorCode.OWN_CARPOOL);
        }
        if (carpool.getStatus() != CompanionStatus.RECRUITING
                || carpool.getDepartureAt().isBefore(LocalDateTime.now(clock))) {
            throw new BusinessException(CarpoolErrorCode.CARPOOL_CLOSED);
        }
        if (carpool.getCurrentCount() >= carpool.getCapacity()) {
            throw new BusinessException(CarpoolErrorCode.CAPACITY_FULL);
        }
        if (carpoolParticipantRepository.existsByCompanionIdAndUserIdAndOutcomeStatus(
                carpool.getId(), userId, OutcomeStatus.PENDING)) {
            throw new BusinessException(CarpoolErrorCode.ALREADY_PARTICIPATING);
        }
        if (companionRequestRepository.existsByCompanionIdAndRequesterIdAndStatus(
                carpool.getId(), userId, CompanionRequestStatus.PENDING)) {
            throw new BusinessException(CarpoolErrorCode.REQUEST_ALREADY_PENDING);
        }
        if (companionRequestRepository.countByCompanionIdAndRequesterId(carpool.getId(), userId)
                >= MAX_REQUESTS_PER_CARPOOL) {
            throw new BusinessException(CarpoolErrorCode.REQUEST_LIMIT_EXCEEDED);
        }
    }

    private CompanionRequest save(CompanionRequest request) {
        try {
            return companionRequestRepository.saveAndFlush(request);
        } catch (DataIntegrityViolationException e) {
            throw new BusinessException(CarpoolErrorCode.REQUEST_ALREADY_PENDING);
        }
    }
}
