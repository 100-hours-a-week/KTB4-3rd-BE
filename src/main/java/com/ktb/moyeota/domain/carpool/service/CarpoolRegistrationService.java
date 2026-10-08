package com.ktb.moyeota.domain.carpool.service;

import com.ktb.moyeota.domain.carpool.error.CarpoolErrorCode;
import com.ktb.moyeota.domain.carpool.model.CarpoolCreateCommand;
import com.ktb.moyeota.domain.carpool.model.CreatedCarpool;
import com.ktb.moyeota.domain.carpool.repository.CarpoolParticipantRepository;
import com.ktb.moyeota.domain.carpool.repository.CarpoolRepository;
import com.ktb.moyeota.domain.chat.entity.ChatRoom;
import com.ktb.moyeota.domain.chat.entity.CompanionParticipant;
import com.ktb.moyeota.domain.chat.repository.ChatRoomRepository;
import com.ktb.moyeota.domain.companion.entity.Companion;
import com.ktb.moyeota.domain.user.entity.User;
import com.ktb.moyeota.domain.user.repository.OwnedCarRepository;
import com.ktb.moyeota.domain.user.repository.UserRepository;
import com.ktb.moyeota.global.exception.BusinessException;
import com.ktb.moyeota.global.exception.CommonErrorCode;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class CarpoolRegistrationService {

    private static final int MAX_DEPARTURE_LEAD_MONTHS = 1;

    private final CarpoolRepository carpoolRepository;
    private final CarpoolParticipantRepository carpoolParticipantRepository;
    private final ChatRoomRepository chatRoomRepository;
    private final UserRepository userRepository;
    private final OwnedCarRepository ownedCarRepository;
    private final Clock clock;

    @Transactional
    public CreatedCarpool create(Long userId, CarpoolCreateCommand command) {
        LocalDateTime departureAt = command.departureAt().truncatedTo(ChronoUnit.MINUTES);
        validateDeparture(departureAt, LocalDateTime.now(clock));
        validateRoute(command);

        User host = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(CommonErrorCode.UNAUTHORIZED));
        if (!ownedCarRepository.existsByUserId(userId)) {
            throw new BusinessException(CarpoolErrorCode.CAR_REGISTRATION_REQUIRED);
        }

        Companion carpool = carpoolRepository.save(Companion.openCarpool(host,
                command.originName(), command.originLat(), command.originLng(),
                command.destName(), command.destLat(), command.destLng(),
                departureAt, command.recruitCount()));
        carpoolParticipantRepository.save(CompanionParticipant.join(carpool, host));
        ChatRoom chatRoom = chatRoomRepository.save(ChatRoom.create(carpool));
        return new CreatedCarpool(carpool.getId(), chatRoom.getId(),
                carpool.getCapacity(), carpool.getCurrentCount(), carpool.getStatus());
    }

    private static void validateDeparture(LocalDateTime departureAt, LocalDateTime now) {
        if (departureAt.isBefore(now.truncatedTo(ChronoUnit.MINUTES))) {
            throw new BusinessException(CarpoolErrorCode.DEPARTURE_TIME_PASSED);
        }
        if (departureAt.toLocalDate().isAfter(now.toLocalDate().plusMonths(MAX_DEPARTURE_LEAD_MONTHS))) {
            throw new BusinessException(CarpoolErrorCode.DEPARTURE_TIME_TOO_FAR);
        }
    }

    private static void validateRoute(CarpoolCreateCommand command) {
        if (command.originLat().compareTo(command.destLat()) == 0
                && command.originLng().compareTo(command.destLng()) == 0) {
            throw new BusinessException(CarpoolErrorCode.SAME_ORIGIN_DEST);
        }
    }
}
