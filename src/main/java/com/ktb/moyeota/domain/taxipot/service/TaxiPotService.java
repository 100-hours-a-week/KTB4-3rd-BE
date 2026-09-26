package com.ktb.moyeota.domain.taxipot.service;

import com.ktb.moyeota.domain.companion.entity.Companion;
import com.ktb.moyeota.domain.companion.entity.CompanionParticipant;
import com.ktb.moyeota.domain.companion.entity.CompanionStatus;
import com.ktb.moyeota.domain.companion.error.CompanionErrorCode;
import com.ktb.moyeota.domain.companion.repository.CompanionParticipantRepository;
import com.ktb.moyeota.domain.companion.repository.CompanionRepository;
import com.ktb.moyeota.domain.taxipot.error.TaxiPotErrorCode;
import com.ktb.moyeota.domain.taxipot.model.CurrentTaxiPot;
import com.ktb.moyeota.domain.taxipot.model.TaxiPotDetail;
import com.ktb.moyeota.domain.taxipot.model.TaxiPotStartCommand;
import com.ktb.moyeota.domain.user.entity.User;
import com.ktb.moyeota.domain.user.repository.UserRepository;
import com.ktb.moyeota.global.exception.BusinessException;
import com.ktb.moyeota.global.exception.CommonErrorCode;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import org.springframework.dao.CannotAcquireLockException;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

@Service
@RequiredArgsConstructor
public class TaxiPotService {

    private static final Duration MAX_DEPARTURE_LEAD_TIME = Duration.ofHours(3);
    private static final int MAX_START_ATTEMPTS = 3;

    private final CompanionRepository companionRepository;
    private final CompanionParticipantRepository companionParticipantRepository;
    private final UserRepository userRepository;
    private final TransactionTemplate transactionTemplate;
    private final Clock clock;

    @Transactional(readOnly = true)
    public Optional<CurrentTaxiPot> findMyCurrent(Long userId) {
        return companionParticipantRepository.findCurrentTaxiPot(userId).map(TaxiPotService::toCurrentTaxiPot);
    }

    public CurrentTaxiPot start(Long userId, TaxiPotStartCommand command) {
        LocalDateTime now = LocalDateTime.now(clock);
        LocalDateTime departureAt = command.departureAt().truncatedTo(ChronoUnit.MINUTES);
        validateDeparture(departureAt, now);
        validateRoute(command);

        for (int attempt = 1; ; attempt++) {
            try {
                return transactionTemplate.execute(status -> joinOrOpen(userId, command, departureAt, now));
            } catch (CannotAcquireLockException e) {
                if (attempt == MAX_START_ATTEMPTS) {
                    throw new BusinessException(TaxiPotErrorCode.MATCH_BUSY);
                }
            }
        }
    }

    private CurrentTaxiPot joinOrOpen(
            Long userId, TaxiPotStartCommand command, LocalDateTime departureAt, LocalDateTime now) {
        User user = userRepository.findByIdForUpdate(userId)
                .orElseThrow(() -> new BusinessException(CommonErrorCode.UNAUTHORIZED));
        if (!user.hasBankAccount()) {
            throw new BusinessException(TaxiPotErrorCode.BANK_ACCOUNT_REQUIRED);
        }
        if (companionParticipantRepository.findCurrentTaxiPot(userId).isPresent()) {
            throw new BusinessException(TaxiPotErrorCode.MATCH_ALREADY_IN_PROGRESS);
        }

        Companion taxiPot = companionRepository.findMatchableTaxiPotForUpdate(
                        command.originLat(), command.originLng(), command.destLat(), command.destLng(), departureAt)
                .map(matched -> joinTaxiPot(matched, user, now))
                .orElseGet(() -> openTaxiPot(user, command, departureAt, now));
        return toCurrentTaxiPot(taxiPot);
    }

    @Transactional(readOnly = true)
    public TaxiPotDetail find(Long userId, Long taxiPotId) {
        return companionRepository.findTaxiPotForParticipant(taxiPotId, userId)
                .map(TaxiPotService::toTaxiPotDetail)
                .orElseThrow(() -> new BusinessException(TaxiPotErrorCode.TAXI_POT_NOT_FOUND));
    }

    @Transactional
    public TaxiPotDetail changeStatus(Long userId, Long taxiPotId, CompanionStatus target) {
        Companion companion = companionRepository.findTaxiPotForParticipantForUpdate(taxiPotId, userId)
                .orElseThrow(() -> new BusinessException(TaxiPotErrorCode.TAXI_POT_NOT_FOUND));
        if (!companion.getHost().getId().equals(userId)) {
            throw new BusinessException(TaxiPotErrorCode.HOST_ONLY);
        }

        switch (target) {
            case IN_PROGRESS -> companion.startRide(LocalDateTime.now(clock));
            case COMPLETED -> companion.completeRide();
            default -> throw new BusinessException(CompanionErrorCode.INVALID_STATE_TRANSITION);
        }
        return toTaxiPotDetail(companion);
    }

    private Companion joinTaxiPot(Companion taxiPot, User user, LocalDateTime now) {
        companionParticipantRepository.save(taxiPot.join(user, now));
        return taxiPot;
    }

    private Companion openTaxiPot(User host, TaxiPotStartCommand command, LocalDateTime departureAt, LocalDateTime now) {
        Companion taxiPot = companionRepository.save(Companion.openTaxiPot(host,
                command.originName(), command.originLat(), command.originLng(),
                command.destName(), command.destLat(), command.destLng(), departureAt));
        companionParticipantRepository.save(CompanionParticipant.join(taxiPot, host, now));
        return taxiPot;
    }

    private static void validateDeparture(LocalDateTime departureAt, LocalDateTime now) {
        if (departureAt.isBefore(now.truncatedTo(ChronoUnit.MINUTES))) {
            throw new BusinessException(TaxiPotErrorCode.DEPARTURE_TIME_PASSED);
        }
        if (departureAt.isAfter(now.plus(MAX_DEPARTURE_LEAD_TIME))) {
            throw new BusinessException(TaxiPotErrorCode.DEPARTURE_TIME_TOO_FAR);
        }
    }

    private static void validateRoute(TaxiPotStartCommand command) {
        if (command.originLat().compareTo(command.destLat()) == 0
                && command.originLng().compareTo(command.destLng()) == 0) {
            throw new BusinessException(TaxiPotErrorCode.SAME_ORIGIN_DEST);
        }
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
