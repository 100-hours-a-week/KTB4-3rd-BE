package com.ktb.moyeota.domain.taxipot.service;

import com.ktb.moyeota.domain.companion.entity.Companion;
import com.ktb.moyeota.domain.companion.entity.CompanionStatus;
import com.ktb.moyeota.domain.companion.repository.CompanionParticipantRepository;
import com.ktb.moyeota.domain.companion.repository.CompanionRepository;
import com.ktb.moyeota.domain.taxipot.error.TaxiPotErrorCode;
import com.ktb.moyeota.domain.taxipot.model.CurrentTaxiPot;
import com.ktb.moyeota.domain.taxipot.model.TaxiPotDetail;
import com.ktb.moyeota.global.exception.BusinessException;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class TaxiPotService {

    private final CompanionRepository companionRepository;
    private final CompanionParticipantRepository companionParticipantRepository;
    private final Clock clock;

    @Transactional(readOnly = true)
    public Optional<CurrentTaxiPot> findMyCurrent(Long userId) {
        return companionParticipantRepository.findCurrentTaxiPot(userId).map(TaxiPotService::toCurrentTaxiPot);
    }

    @Transactional(readOnly = true)
    public TaxiPotDetail find(Long userId, Long taxiPotId) {
        return companionRepository.findTaxiPotForParticipant(taxiPotId, userId)
                .map(TaxiPotService::toTaxiPotDetail)
                .orElseThrow(() -> new BusinessException(TaxiPotErrorCode.TAXI_POT_NOT_FOUND));
    }

    @Transactional
    public TaxiPotDetail changeStatus(Long userId, Long taxiPotId, CompanionStatus target) {
        Companion companion = companionRepository.findTaxiPotForParticipant(taxiPotId, userId)
                .orElseThrow(() -> new BusinessException(TaxiPotErrorCode.TAXI_POT_NOT_FOUND));
        if (!companion.getHost().getId().equals(userId)) {
            throw new BusinessException(TaxiPotErrorCode.HOST_ONLY);
        }

        LocalDateTime now = LocalDateTime.now(clock);
        int changed = switch (target) {
            case IN_PROGRESS -> companionRepository.startRide(taxiPotId, userId, now);
            case COMPLETED -> companionRepository.completeRide(taxiPotId, userId);
            default -> throw new BusinessException(TaxiPotErrorCode.INVALID_STATE_TRANSITION);
        };
        if (changed == 0) {
            throw new BusinessException(rejectionReason(companion, target, now));
        }
        return toTaxiPotDetail(companion);
    }

    private static TaxiPotErrorCode rejectionReason(Companion companion, CompanionStatus target, LocalDateTime now) {
        if (target == CompanionStatus.COMPLETED || companion.getStatus() != CompanionStatus.RECRUITING) {
            return TaxiPotErrorCode.INVALID_STATE_TRANSITION;
        }
        if (companion.getCurrentCount() < 2) {
            return TaxiPotErrorCode.NOT_ENOUGH_PARTICIPANTS;
        }
        if (companion.getDepartureAt().isAfter(now)) {
            return TaxiPotErrorCode.DEPARTURE_NOT_REACHED;
        }
        return TaxiPotErrorCode.INVALID_STATE_TRANSITION;
    }

    private static CurrentTaxiPot toCurrentTaxiPot(Companion companion) {
        return new CurrentTaxiPot(
                companion.getId(), companion.getStatus(), companion.getCurrentCount(), companion.getCapacity());
    }

    private static TaxiPotDetail toTaxiPotDetail(Companion companion) {
        return new TaxiPotDetail(
                companion.getId(),
                companion.getStatus(),
                companion.getOriginName(),
                companion.getDestName(),
                companion.getDepartureAt(),
                companion.getCurrentCount(),
                companion.getCapacity(),
                companion.getHost().getId());
    }
}
