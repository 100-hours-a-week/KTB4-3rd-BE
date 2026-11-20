package com.ktb.moyeota.domain.chat.service;

import com.ktb.moyeota.domain.chat.dto.ChatRoomDetailResponse;
import com.ktb.moyeota.domain.chat.dto.ChatRoomListResponse;
import com.ktb.moyeota.domain.chat.dto.ReadMarkerRequest;
import com.ktb.moyeota.domain.chat.dto.ReadMarkerResponse;
import com.ktb.moyeota.domain.chat.entity.ChatRoom;
import com.ktb.moyeota.domain.chat.entity.CompanionParticipant;
import com.ktb.moyeota.domain.chat.exception.ChatErrorCode;
import com.ktb.moyeota.domain.chat.repository.ChatRoomRepository;
import com.ktb.moyeota.domain.chat.repository.CompanionParticipantRepository;
import com.ktb.moyeota.domain.companion.entity.Companion;
import com.ktb.moyeota.domain.companion.entity.CompanionKind;
import com.ktb.moyeota.domain.companion.entity.CompanionStatus;
import com.ktb.moyeota.domain.rating.repository.CompanionRatingRepository;
import com.ktb.moyeota.domain.settlement.entity.Settlement;
import com.ktb.moyeota.domain.settlement.entity.SettlementStatus;
import com.ktb.moyeota.domain.settlement.repository.SettlementRepository;
import com.ktb.moyeota.domain.user.entity.User;
import com.ktb.moyeota.global.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ChatRoomService {

    private final ChatRoomRepository chatRoomRepository;
    private final CompanionParticipantRepository companionParticipantRepository;
    private final ReadMarkerStore readMarkerStore;
    private final ChatRoomListReader chatRoomListReader;
    private final SettlementRepository settlementRepository;
    private final CompanionRatingRepository companionRatingRepository;

    public ChatRoomListResponse findMyChatRooms(Long userId, CompanionKind kind, String cursor) {
        return chatRoomListReader.findMyChatRooms(userId, kind, cursor);
    }

    @Transactional(readOnly = true)
    public boolean isActiveParticipant(Long roomId, Long userId) {
        return companionParticipantRepository.existsActiveByChatRoomIdAndUserId(roomId, userId);
    }

    @Transactional(readOnly = true)
    public ChatRoomDetailResponse findDetail(Long userId, Long roomId) {
        CompanionParticipant participant = companionParticipantRepository
                .findActiveByChatRoomIdAndUserId(roomId, userId)
                .orElseThrow(() -> new BusinessException(ChatErrorCode.CHATROOM_NOT_FOUND));

        ChatRoom chatRoom = chatRoomRepository.findByIdWithCompanion(roomId)
                .orElseThrow(() -> new BusinessException(ChatErrorCode.CHATROOM_NOT_FOUND));

        ReadMarkerResponse readMarker = readMarkerStore.current(participant);
        SettlementStatus settlementStatus = resolveSettlementStatus(chatRoom.getCompanion());
        Boolean rated = resolveRated(userId, chatRoom.getCompanion(), participant, settlementStatus);

        return ChatRoomDetailResponse.of(chatRoom, readMarker, settlementStatus, rated);
    }

    @Transactional
    public ReadMarkerResponse markRead(Long userId, Long roomId, ReadMarkerRequest request) {
        CompanionParticipant participant = companionParticipantRepository
                .findActiveByChatRoomIdAndUserId(roomId, userId)
                .orElseThrow(() -> new BusinessException(ChatErrorCode.CHATROOM_NOT_FOUND));

        return readMarkerStore.markRead(participant, roomId, request);
    }

    @Transactional
    public void createChatRoomForCompanion(Companion companion, User host) {
        chatRoomRepository.save(ChatRoom.create(companion));
        companionParticipantRepository.save(CompanionParticipant.join(companion, host));
    }

    private SettlementStatus resolveSettlementStatus(Companion companion) {
        if (companion.getStatus() != CompanionStatus.COMPLETED || companion.getKind() != CompanionKind.TAXI_POT) {
            return null;
        }
        return settlementRepository.findWithPayeeByCompanionId(companion.getId())
                .map(Settlement::getStatus)
                .orElse(null);
    }

    private Boolean resolveRated(
            Long userId, Companion companion, CompanionParticipant viewer, SettlementStatus settlementStatus) {
        if (!isRatingOpen(companion, settlementStatus) || !viewer.isCompleted()
                || !companionParticipantRepository.existsOtherCompletedParticipant(companion.getId(), userId)) {
            return null;
        }
        return companionRatingRepository.existsByCompanionIdAndRaterId(companion.getId(), userId);
    }

    private boolean isRatingOpen(Companion companion, SettlementStatus settlementStatus) {
        if (companion.getStatus() != CompanionStatus.COMPLETED) {
            return false;
        }
        return switch (companion.getKind()) {
            case TAXI_POT -> settlementStatus == SettlementStatus.COMPLETED;
            case CARPOOL -> true;
            case COMPANION -> false;
        };
    }
}
