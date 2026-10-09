package com.ktb.moyeota.domain.carpool.service;

import com.ktb.moyeota.domain.carpool.error.CarpoolErrorCode;
import com.ktb.moyeota.domain.carpool.event.CarpoolRideStartedEvent;
import com.ktb.moyeota.domain.carpool.model.CarpoolDetail;
import com.ktb.moyeota.domain.carpool.repository.CarpoolParticipantRepository;
import com.ktb.moyeota.domain.carpool.repository.CarpoolRepository;
import com.ktb.moyeota.domain.chat.entity.ChatRoom;
import com.ktb.moyeota.domain.chat.entity.CompanionParticipant;
import com.ktb.moyeota.domain.chat.repository.ChatRoomRepository;
import com.ktb.moyeota.domain.chat.service.ChatSystemMessageService;
import com.ktb.moyeota.domain.companion.entity.Companion;
import com.ktb.moyeota.domain.companion.entity.CompanionStatus;
import com.ktb.moyeota.domain.companion.error.CompanionErrorCode;
import com.ktb.moyeota.global.exception.BusinessException;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class CarpoolRideService {

    private static final Duration AUTO_CANCEL_AFTER_DEPARTURE = Duration.ofHours(12);

    private final CarpoolRepository carpoolRepository;
    private final CarpoolParticipantRepository carpoolParticipantRepository;
    private final ChatRoomRepository chatRoomRepository;
    private final ChatSystemMessageService chatSystemMessageService;
    private final CarpoolDetailReader carpoolDetailReader;
    private final ApplicationEventPublisher eventPublisher;
    private final Clock clock;

    @Transactional
    public CarpoolDetail changeStatus(Long userId, Long carpoolId, CompanionStatus target) {
        Companion carpool = carpoolRepository.findCarpoolForParticipantForUpdate(carpoolId, userId)
                .orElseThrow(() -> new BusinessException(CarpoolErrorCode.CARPOOL_NOT_FOUND));
        if (!carpool.isHostedBy(userId)) {
            throw new BusinessException(CarpoolErrorCode.HOST_ONLY);
        }

        switch (target) {
            case IN_PROGRESS -> startRide(carpool);
            case COMPLETED -> completeRide(carpool);
            default -> throw new BusinessException(CompanionErrorCode.INVALID_STATE_TRANSITION);
        }
        return carpoolDetailReader.read(carpool);
    }

    @Transactional
    public void leave(Long userId, Long carpoolId) {
        Companion carpool = carpoolRepository.findCarpoolForParticipantForUpdate(carpoolId, userId)
                .orElseThrow(() -> new BusinessException(CarpoolErrorCode.CARPOOL_NOT_FOUND));
        CompanionParticipant leaver = carpoolParticipantRepository.findByCompanionIdAndUserId(carpoolId, userId)
                .orElseThrow(() -> new BusinessException(CarpoolErrorCode.CARPOOL_NOT_FOUND));

        if (leaver.isCompleted()) {
            return;
        }
        if (carpool.isHostedBy(userId)) {
            throw new BusinessException(CarpoolErrorCode.HOST_CANNOT_LEAVE);
        }
        carpool.leaveCarpool(leaver);
        chatSystemMessageService.leave(findChatRoom(carpool), leaver.getUser());
    }

    @Transactional(readOnly = true)
    public List<Long> findRideStartDueIds() {
        return carpoolRepository.findRideStartDueIds(LocalDateTime.now(clock));
    }

    @Transactional
    public void requestRideStart(Long carpoolId) {
        chatSystemMessageService.requestRideStart(findChatRoom(carpoolRepository.getReferenceById(carpoolId)));
    }

    @Transactional(readOnly = true)
    public List<Long> findRideEndDueIds() {
        return carpoolRepository.findRideEndDueIds(LocalDateTime.now(clock));
    }

    @Transactional
    public void requestRideEnd(Long carpoolId) {
        chatSystemMessageService.requestRideEnd(findChatRoom(carpoolRepository.getReferenceById(carpoolId)));
    }

    @Transactional(readOnly = true)
    public List<Long> findAutoCancelDueIds() {
        return carpoolRepository.findAutoCancelDueIds(LocalDateTime.now(clock).minus(AUTO_CANCEL_AFTER_DEPARTURE));
    }

    @Transactional
    public void autoCancel(Long carpoolId) {
        carpoolRepository.findRecruitingCarpoolForUpdate(carpoolId).ifPresent(carpool -> {
            carpool.cancel(carpoolParticipantRepository.findPendingParticipants(carpoolId));
            findChatRoom(carpool).close();
        });
    }

    @Transactional
    public void estimateArrival(Long carpoolId, LocalDateTime etaAt) {
        carpoolRepository.findById(carpoolId).ifPresent(carpool -> carpool.estimateArrival(etaAt));
    }

    private void startRide(Companion carpool) {
        LocalDateTime startedAt = LocalDateTime.now(clock);
        carpool.startRide(startedAt);
        chatSystemMessageService.rideStarted(findChatRoom(carpool));
        eventPublisher.publishEvent(new CarpoolRideStartedEvent(carpool.getId(), startedAt,
                carpool.getOriginLat(), carpool.getOriginLng(), carpool.getDestLat(), carpool.getDestLng()));
    }

    private void completeRide(Companion carpool) {
        carpool.completeRide(carpoolParticipantRepository.findPendingParticipants(carpool.getId()));
        ChatRoom chatRoom = findChatRoom(carpool);
        chatSystemMessageService.rideEnded(chatRoom);
        chatRoom.close();
    }

    private ChatRoom findChatRoom(Companion carpool) {
        return chatRoomRepository.findByCompanionId(carpool.getId())
                .orElseThrow(() -> new IllegalStateException("카풀에 채팅방이 없다: " + carpool.getId()));
    }
}
