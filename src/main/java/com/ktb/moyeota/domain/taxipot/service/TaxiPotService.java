package com.ktb.moyeota.domain.taxipot.service;

import com.ktb.moyeota.domain.chat.entity.ChatRoom;
import com.ktb.moyeota.domain.chat.entity.CompanionParticipant;
import com.ktb.moyeota.domain.chat.repository.ChatRoomRepository;
import com.ktb.moyeota.domain.chat.service.ChatSystemMessageService;
import com.ktb.moyeota.domain.companion.entity.Companion;
import com.ktb.moyeota.domain.companion.entity.CompanionStatus;
import com.ktb.moyeota.domain.companion.error.CompanionErrorCode;
import com.ktb.moyeota.domain.taxipot.error.TaxiPotErrorCode;
import com.ktb.moyeota.domain.taxipot.event.TaxiPotRideStartedEvent;
import com.ktb.moyeota.domain.taxipot.model.CurrentTaxiPot;
import com.ktb.moyeota.domain.taxipot.model.TaxiPotDetail;
import com.ktb.moyeota.domain.taxipot.model.TaxiPotStartCommand;
import com.ktb.moyeota.domain.taxipot.repository.TaxiPotParticipantRepository;
import com.ktb.moyeota.domain.taxipot.repository.TaxiPotRepository;
import com.ktb.moyeota.domain.user.entity.User;
import com.ktb.moyeota.domain.user.repository.UserRepository;
import com.ktb.moyeota.global.exception.BusinessException;
import com.ktb.moyeota.global.exception.CommonErrorCode;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.CannotAcquireLockException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

@Service
@RequiredArgsConstructor
public class TaxiPotService {

    private static final Duration MAX_DEPARTURE_LEAD_TIME = Duration.ofHours(3);
    private static final int MAX_START_ATTEMPTS = 3;

    private final TaxiPotRepository taxiPotRepository;
    private final TaxiPotParticipantRepository taxiPotParticipantRepository;
    private final UserRepository userRepository;
    private final ChatRoomRepository chatRoomRepository;
    private final ChatSystemMessageService chatSystemMessageService;
    private final ApplicationEventPublisher eventPublisher;
    private final TransactionTemplate transactionTemplate;
    private final Clock clock;

    @Transactional(readOnly = true)
    public Optional<CurrentTaxiPot> findMyCurrent(Long userId) {
        return taxiPotParticipantRepository.findCurrentTaxiPot(userId)
                .map(taxiPot -> toCurrentTaxiPot(taxiPot, findChatRoom(taxiPot).getId()));
    }

    public CurrentTaxiPot start(Long userId, TaxiPotStartCommand command) {
        LocalDateTime now = LocalDateTime.now(clock);
        LocalDateTime departureAt = command.departureAt().truncatedTo(ChronoUnit.MINUTES);
        validateDeparture(departureAt, now);
        validateRoute(command);

        for (int attempt = 1; ; attempt++) {
            try {
                return transactionTemplate.execute(status -> joinOrOpen(userId, command, departureAt));
            } catch (CannotAcquireLockException e) {
                if (attempt == MAX_START_ATTEMPTS) {
                    throw new BusinessException(TaxiPotErrorCode.MATCH_BUSY);
                }
            }
        }
    }

    private CurrentTaxiPot joinOrOpen(Long userId, TaxiPotStartCommand command, LocalDateTime departureAt) {
        User user = userRepository.findByIdForUpdate(userId)
                .orElseThrow(() -> new BusinessException(CommonErrorCode.UNAUTHORIZED));
        if (!user.hasBankAccount()) {
            throw new BusinessException(TaxiPotErrorCode.BANK_ACCOUNT_REQUIRED);
        }
        if (taxiPotParticipantRepository.findCurrentTaxiPot(userId).isPresent()) {
            throw new BusinessException(TaxiPotErrorCode.MATCH_ALREADY_IN_PROGRESS);
        }

        ChatRoom chatRoom = taxiPotRepository.findMatchableTaxiPotForUpdate(
                        command.originLat(), command.originLng(), command.destLat(), command.destLng(), departureAt)
                .map(matched -> joinTaxiPot(matched, user))
                .orElseGet(() -> openTaxiPot(user, command, departureAt));
        chatSystemMessageService.enter(chatRoom, user);
        return toCurrentTaxiPot(chatRoom.getCompanion(), chatRoom.getId());
    }

    @Transactional
    public void leave(Long userId, Long taxiPotId) {
        Companion taxiPot = taxiPotRepository.findTaxiPotForParticipantForUpdate(taxiPotId, userId)
                .orElseThrow(() -> new BusinessException(TaxiPotErrorCode.TAXI_POT_NOT_FOUND));
        CompanionParticipant leaver = taxiPotParticipantRepository.findByCompanionIdAndUserId(taxiPotId, userId)
                .orElseThrow(() -> new BusinessException(TaxiPotErrorCode.TAXI_POT_NOT_FOUND));
        CompanionParticipant nextHost = taxiPot.isHostedBy(userId)
                ? taxiPotParticipantRepository.findNextHost(taxiPotId, userId).orElse(null)
                : null;

        taxiPot.leave(leaver, nextHost);
        if (!leaver.isSettled()) {
            chatSystemMessageService.leave(findChatRoom(taxiPot), leaver.getUser());
        }
    }

    @Transactional(readOnly = true)
    public TaxiPotDetail find(Long userId, Long taxiPotId) {
        return taxiPotRepository.findTaxiPotForParticipant(taxiPotId, userId)
                .map(this::toTaxiPotDetail)
                .orElseThrow(() -> new BusinessException(TaxiPotErrorCode.TAXI_POT_NOT_FOUND));
    }

    @Transactional
    public TaxiPotDetail changeStatus(Long userId, Long taxiPotId, CompanionStatus target) {
        Companion companion = taxiPotRepository.findTaxiPotForParticipantForUpdate(taxiPotId, userId)
                .orElseThrow(() -> new BusinessException(TaxiPotErrorCode.TAXI_POT_NOT_FOUND));
        if (!companion.getHost().getId().equals(userId)) {
            throw new BusinessException(TaxiPotErrorCode.HOST_ONLY);
        }

        switch (target) {
            case IN_PROGRESS -> startRide(companion);
            case COMPLETED -> completeRide(companion);
            default -> throw new BusinessException(CompanionErrorCode.INVALID_STATE_TRANSITION);
        }
        return toTaxiPotDetail(companion);
    }

    @Transactional(readOnly = true)
    public List<Long> findRideStartDueIds() {
        return taxiPotRepository.findRideStartDueIds(LocalDateTime.now(clock));
    }

    @Transactional
    public void requestRideStart(Long taxiPotId) {
        Companion taxiPot = taxiPotRepository.getReferenceById(taxiPotId);
        chatSystemMessageService.requestRideStart(findChatRoom(taxiPot));
    }

    @Transactional(readOnly = true)
    public List<Long> findRideEndDueIds() {
        return taxiPotRepository.findRideEndDueIds(LocalDateTime.now(clock));
    }

    @Transactional
    public void requestRideEnd(Long taxiPotId) {
        Companion taxiPot = taxiPotRepository.getReferenceById(taxiPotId);
        chatSystemMessageService.requestRideEnd(findChatRoom(taxiPot));
    }

    @Transactional
    public void estimateArrival(Long taxiPotId, LocalDateTime etaAt) {
        taxiPotRepository.findById(taxiPotId).ifPresent(taxiPot -> taxiPot.estimateArrival(etaAt));
    }

    private void startRide(Companion taxiPot) {
        LocalDateTime startedAt = LocalDateTime.now(clock);
        taxiPot.startRide(startedAt);
        chatSystemMessageService.rideStarted(findChatRoom(taxiPot));
        eventPublisher.publishEvent(new TaxiPotRideStartedEvent(taxiPot.getId(), startedAt,
                taxiPot.getOriginLat(), taxiPot.getOriginLng(), taxiPot.getDestLat(), taxiPot.getDestLng()));
    }

    private void completeRide(Companion taxiPot) {
        taxiPot.completeRide();
        chatSystemMessageService.rideEnded(findChatRoom(taxiPot));
    }

    private ChatRoom joinTaxiPot(Companion taxiPot, User user) {
        CompanionParticipant previous = taxiPotParticipantRepository
                .findByCompanionIdAndUserId(taxiPot.getId(), user.getId())
                .orElse(null);
        taxiPotParticipantRepository.save(taxiPot.join(user, previous));
        return findChatRoom(taxiPot);
    }

    private ChatRoom findChatRoom(Companion taxiPot) {
        return chatRoomRepository.findByCompanionId(taxiPot.getId())
                .orElseThrow(() -> new IllegalStateException("택시팟에 채팅방이 없다: " + taxiPot.getId()));
    }

    private ChatRoom openTaxiPot(User host, TaxiPotStartCommand command, LocalDateTime departureAt) {
        Companion taxiPot = taxiPotRepository.save(Companion.openTaxiPot(host,
                command.originName(), command.originLat(), command.originLng(),
                command.destName(), command.destLat(), command.destLng(), departureAt));
        taxiPotParticipantRepository.save(CompanionParticipant.join(taxiPot, host));
        return chatRoomRepository.save(ChatRoom.create(taxiPot));
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

    private static CurrentTaxiPot toCurrentTaxiPot(Companion companion, Long chatRoomId) {
        return new CurrentTaxiPot(
                companion.getId(), chatRoomId, companion.getStatus(), companion.getCurrentCount(), companion.getCapacity());
    }

    private TaxiPotDetail toTaxiPotDetail(Companion companion) {
        return new TaxiPotDetail(
                companion.getId(),
                findChatRoom(companion).getId(),
                companion.getStatus(),
                companion.getOriginName(),
                companion.getDestName(),
                companion.getDepartureAt(),
                companion.getCurrentCount(),
                companion.getCapacity(),
                companion.getHost().getId());
    }
}
