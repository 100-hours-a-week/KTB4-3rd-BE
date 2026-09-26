package com.ktb.moyeota.domain.chat.service;

import com.ktb.moyeota.domain.chat.dto.ChatLeaveResponse;
import com.ktb.moyeota.domain.chat.dto.ChatParticipateResponse;
import com.ktb.moyeota.domain.chat.entity.ChatRoom;
import com.ktb.moyeota.domain.chat.entity.CompanionParticipant;
import com.ktb.moyeota.domain.chat.exception.ChatErrorCode;
import com.ktb.moyeota.domain.chat.repository.ChatRoomRepository;
import com.ktb.moyeota.domain.chat.repository.CompanionParticipantRepository;
import com.ktb.moyeota.domain.companion.entity.Companion;
import com.ktb.moyeota.domain.companion.entity.CompanionStatus;
import com.ktb.moyeota.domain.companionpost.repository.CompanionPostRepository;
import com.ktb.moyeota.domain.user.entity.User;
import com.ktb.moyeota.domain.user.repository.UserRepository;
import com.ktb.moyeota.global.exception.BusinessException;
import com.ktb.moyeota.global.exception.CommonErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ChatParticipationService {

    private final CompanionPostRepository companionPostRepository;
    private final CompanionParticipantRepository companionParticipantRepository;
    private final ChatRoomRepository chatRoomRepository;
    private final UserRepository userRepository;

    @Transactional
    public ChatParticipateResponse participate(Long userId, Long companionId) {
        companionParticipantRepository.findActiveByCompanionIdAndUserId(companionId, userId)
                .ifPresent(p -> {
                    throw new BusinessException(ChatErrorCode.ALREADY_PARTICIPATING);
                });

        Companion companion = companionPostRepository.findById(companionId)
                .orElseThrow(() -> new BusinessException(ChatErrorCode.COMPANION_NOT_FOUND));
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(CommonErrorCode.UNAUTHORIZED));

        int updatedRows = chatRoomRepository.increaseCurrentCountIfRecruitingAndNotFull(
                companionId, CompanionStatus.RECRUITING);
        if (updatedRows == 0) {
            throw new BusinessException(ChatErrorCode.COMPANION_NOT_JOINABLE);
        }

        CompanionParticipant previous = companionParticipantRepository
                .findByCompanionIdAndUserId(companionId, userId)
                .orElse(null);
        CompanionParticipant participant = saveParticipant(CompanionParticipant.join(companion, user, previous));

        ChatRoom chatRoom = chatRoomRepository.findByCompanionId(companionId)
                .orElseThrow(() -> new BusinessException(ChatErrorCode.COMPANION_CHATROOM_NOT_FOUND));

        return new ChatParticipateResponse(participant.getId(), chatRoom.getId());
    }

    private CompanionParticipant saveParticipant(CompanionParticipant participant) {
        try {
            return companionParticipantRepository.save(participant);
        } catch (DataIntegrityViolationException e) {
            throw new BusinessException(ChatErrorCode.ALREADY_PARTICIPATING);
        }
    }

    @Transactional
    public ChatLeaveResponse leave(Long userId, Long companionId) {
        CompanionParticipant participant = companionParticipantRepository
                .findActiveByCompanionIdAndUserId(companionId, userId)
                .orElseThrow(() -> new BusinessException(ChatErrorCode.NOT_PARTICIPATING));

        Companion companion = companionPostRepository.findById(companionId)
                .orElseThrow(() -> new BusinessException(ChatErrorCode.COMPANION_NOT_FOUND));

        ChatRoom chatRoom = chatRoomRepository.findByCompanionId(companionId)
                .orElseThrow(() -> new BusinessException(ChatErrorCode.COMPANION_CHATROOM_NOT_FOUND));

        boolean isHost = companion.getHost().getId().equals(userId);
        if (isHost) {
            boolean isAlone = companion.getCurrentCount() <= 1;
            if (isAlone) {
                chatRoom.close();
            } else {
                CompanionParticipant nextHost = companionParticipantRepository
                        .findFirstByCompanionIdAndUserIdNotAndLeftAtIsNullOrderByJoinedAtAsc(companionId, userId)
                        .orElseThrow(() -> new BusinessException(ChatErrorCode.COMPANION_NEXT_HOST_NOT_FOUND));
                companion.transferHost(nextHost.getUser());
            }
        }

        participant.leave();
        chatRoomRepository.decreaseCurrentCount(companionId);

        // TODO: companion_participants.outcome_status 갱신
        // TODO: [확인 필요] SYSTEM_LEAVE 메시지 생성도 참여하기와 동일한 이유(clientMessageId 미정)로 보류.

        return new ChatLeaveResponse(chatRoom.getId());
    }
}
