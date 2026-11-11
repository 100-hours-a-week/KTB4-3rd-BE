package com.ktb.moyeota.domain.carpool.service;

import com.ktb.moyeota.domain.carpool.entity.CompanionRequest;
import com.ktb.moyeota.domain.carpool.entity.CompanionRequestStatus;
import com.ktb.moyeota.domain.carpool.entity.RejectReason;
import com.ktb.moyeota.domain.carpool.error.CarpoolErrorCode;
import com.ktb.moyeota.domain.carpool.model.HandledJoinRequest;
import com.ktb.moyeota.domain.carpool.model.SentJoinRequest;
import com.ktb.moyeota.domain.carpool.repository.CarpoolParticipantRepository;
import com.ktb.moyeota.domain.carpool.repository.CarpoolRepository;
import com.ktb.moyeota.domain.carpool.repository.CompanionRequestRepository;
import com.ktb.moyeota.domain.chat.entity.ChatRoom;
import com.ktb.moyeota.domain.chat.entity.CompanionParticipant;
import com.ktb.moyeota.domain.chat.entity.OutcomeStatus;
import com.ktb.moyeota.domain.chat.repository.ChatRoomRepository;
import com.ktb.moyeota.domain.chat.service.ChatSystemMessageService;
import com.ktb.moyeota.domain.companion.entity.Companion;
import com.ktb.moyeota.domain.companion.entity.CompanionStatus;
import com.ktb.moyeota.domain.notification.entity.NotificationType;
import com.ktb.moyeota.domain.notification.service.NotificationCommandService;
import com.ktb.moyeota.domain.user.entity.User;
import com.ktb.moyeota.domain.user.repository.UserRepository;
import com.ktb.moyeota.global.exception.BusinessException;
import com.ktb.moyeota.global.exception.CommonErrorCode;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class CarpoolRequestService {

    private static final int MAX_REQUESTS_PER_CARPOOL = 3;
    private static final int EXPIRE_BATCH_SIZE = 100;
    private static final String CAPACITY_FULL_MESSAGE = "카풀 자리가 모두 찼어요. 다른 카풀을 찾아보세요";
    private static final String DEPARTED_MESSAGE = "출발 시간이 지나 동승 요청이 종료됐어요";

    private final CarpoolRepository carpoolRepository;
    private final CarpoolParticipantRepository carpoolParticipantRepository;
    private final CompanionRequestRepository companionRequestRepository;
    private final UserRepository userRepository;
    private final ChatRoomRepository chatRoomRepository;
    private final ChatSystemMessageService chatSystemMessageService;
    private final NotificationCommandService notificationCommandService;
    private final Clock clock;

    @Transactional
    public SentJoinRequest send(Long userId, Long carpoolId, String content) {
        User requester = findActiveUser(userId);
        Companion carpool = carpoolRepository.findCarpool(carpoolId)
                .orElseThrow(() -> new BusinessException(CarpoolErrorCode.CARPOOL_NOT_FOUND));
        validateRequestable(carpool, userId);

        CompanionRequest request = save(CompanionRequest.send(carpool, requester, content));
        notificationCommandService.createForChatRoom(carpool.getHost(), NotificationType.CARPOOL_REQUEST_RECEIVED,
                request.getId(), findChatRoom(carpool), requester.getNickname() + "님이 동승을 요청했어요");
        return new SentJoinRequest(request.getId(), carpoolId, request.getStatus(), request.getCreatedAt());
    }

    @Transactional
    public HandledJoinRequest handle(Long userId, Long carpoolId, Long requestId, CompanionRequestStatus result) {
        findActiveUser(userId);
        Companion carpool = carpoolRepository.findCarpoolForUpdate(carpoolId)
                .orElseThrow(() -> new BusinessException(CarpoolErrorCode.CARPOOL_REQUEST_NOT_FOUND));
        if (!carpool.isHostedBy(userId)) {
            throw new BusinessException(CarpoolErrorCode.HOST_ONLY);
        }
        CompanionRequest request = companionRequestRepository.findForUpdateByIdAndCompanionId(requestId, carpoolId)
                .orElseThrow(() -> new BusinessException(CarpoolErrorCode.CARPOOL_REQUEST_NOT_FOUND));

        return switch (result) {
            case ACCEPTED -> accept(carpool, request);
            case REJECTED -> reject(carpool, request);
            case PENDING -> throw new IllegalArgumentException("대기 상태로는 처리할 수 없다: " + requestId);
        };
    }

    private HandledJoinRequest accept(Companion carpool, CompanionRequest request) {
        if (request.getStatus() != CompanionRequestStatus.PENDING) {
            throw new BusinessException(CarpoolErrorCode.REQUEST_ALREADY_HANDLED);
        }
        validateOpen(carpool);
        if (carpool.getCurrentCount() >= carpool.getCapacity()) {
            throw new BusinessException(CarpoolErrorCode.CAPACITY_FULL);
        }
        request.accept();
        User requester = request.getRequester();
        CompanionParticipant previous = carpoolParticipantRepository
                .findForUpdateByCompanionIdAndUserId(carpool.getId(), requester.getId())
                .orElse(null);
        carpoolParticipantRepository.save(carpool.join(requester, previous));
        ChatRoom chatRoom = findChatRoom(carpool);
        chatSystemMessageService.enter(chatRoom, requester);
        notificationCommandService.createForChatRoom(requester, NotificationType.CARPOOL_REQUEST_ACCEPTED,
                request.getId(), chatRoom, carpool.getHost().getNickname() + "님이 동승 요청을 수락했어요");
        if (carpool.getCurrentCount() == carpool.getCapacity()) {
            rejectRemaining(carpool.getId(), RejectReason.CAPACITY_FULL,
                    NotificationType.CARPOOL_REQUEST_REJECTED, CAPACITY_FULL_MESSAGE);
        }
        return new HandledJoinRequest(request.getId(), request.getStatus(), chatRoom.getId(),
                carpool.getCurrentCount(), carpool.getCapacity());
    }

    private HandledJoinRequest reject(Companion carpool, CompanionRequest request) {
        request.reject(RejectReason.HOST);
        notificationCommandService.createWithoutTarget(request.getRequester(), NotificationType.CARPOOL_REQUEST_REJECTED,
                request.getId(), carpool.getHost().getNickname() + "님이 동승 요청을 거절했어요");
        return HandledJoinRequest.rejected(request.getId());
    }

    @Transactional(readOnly = true)
    public List<Long> findExpireDueIds() {
        return companionRequestRepository.findDepartedCarpoolIdsWithPending(
                LocalDateTime.now(clock), PageRequest.of(0, EXPIRE_BATCH_SIZE));
    }

    @Transactional
    public void expireDepartedRequests(Long carpoolId) {
        carpoolRepository.findCarpoolForUpdate(carpoolId)
                .orElseThrow(() -> new BusinessException(CarpoolErrorCode.CARPOOL_NOT_FOUND));
        rejectRemaining(carpoolId, RejectReason.DEPARTED, NotificationType.CARPOOL_REQUEST_EXPIRED, DEPARTED_MESSAGE);
    }

    private void rejectRemaining(Long carpoolId, RejectReason reason, NotificationType type, String content) {
        for (CompanionRequest pending : companionRequestRepository.findPendingWithRequester(carpoolId)) {
            pending.reject(reason);
            notificationCommandService.createWithoutTarget(pending.getRequester(), type, pending.getId(), content);
        }
    }

    private ChatRoom findChatRoom(Companion carpool) {
        return chatRoomRepository.findByCompanionId(carpool.getId())
                .orElseThrow(() -> new IllegalStateException("카풀에 채팅방이 없다: " + carpool.getId()));
    }

    private User findActiveUser(Long userId) {
        return userRepository.findById(userId)
                .filter(user -> !user.isWithdrawn())
                .orElseThrow(() -> new BusinessException(CommonErrorCode.UNAUTHORIZED));
    }

    private void validateOpen(Companion carpool) {
        if (carpool.getStatus() != CompanionStatus.RECRUITING
                || carpool.getDepartureAt().isBefore(LocalDateTime.now(clock))) {
            throw new BusinessException(CarpoolErrorCode.CARPOOL_CLOSED);
        }
    }

    private void validateRequestable(Companion carpool, Long userId) {
        if (carpool.isHostedBy(userId)) {
            throw new BusinessException(CarpoolErrorCode.OWN_CARPOOL);
        }
        validateOpen(carpool);
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
        if (companionRequestRepository.countByCompanionIdAndRequesterIdAndRejectReason(
                carpool.getId(), userId, RejectReason.HOST) >= MAX_REQUESTS_PER_CARPOOL) {
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
