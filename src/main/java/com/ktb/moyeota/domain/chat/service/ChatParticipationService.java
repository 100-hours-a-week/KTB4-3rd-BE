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
    private final ChatSystemMessageService chatSystemMessageService;

    @Transactional
    public ChatParticipateResponse participate(Long userId, Long companionId) {
        Companion companion = companionPostRepository.findCompanionPostById(companionId)
                .orElseThrow(() -> new BusinessException(ChatErrorCode.COMPANION_NOT_FOUND));
        companionParticipantRepository.findActiveByCompanionIdAndUserId(companionId, userId)
                .ifPresent(p -> {
                    throw new BusinessException(ChatErrorCode.ALREADY_PARTICIPATING);
                });
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

        chatSystemMessageService.enter(chatRoom, user);

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

        Companion companion = companionPostRepository.findCompanionPostById(companionId)
                .orElseThrow(() -> new BusinessException(ChatErrorCode.COMPANION_NOT_FOUND));

        ChatRoom chatRoom = chatRoomRepository.findByCompanionId(companionId)
                .orElseThrow(() -> new BusinessException(ChatErrorCode.COMPANION_CHATROOM_NOT_FOUND));

        boolean isHost = companion.getHost().getId().equals(userId);
        boolean isLastParticipant = isHost && companion.getCurrentCount() <= 1;
        if (isLastParticipant) {
            chatRoom.close();
        } else if (isHost) {
            CompanionParticipant nextHost = companionParticipantRepository
                    .findFirstByCompanionIdAndUserIdNotAndLeftAtIsNullOrderByJoinedAtAsc(companionId, userId)
                    .orElseThrow(() -> new BusinessException(ChatErrorCode.COMPANION_NEXT_HOST_NOT_FOUND));
            companion.transferHost(nextHost.getUser());
        }

        participant.leave();

        if (!isLastParticipant) {
            chatSystemMessageService.leave(chatRoom, participant.getUser());
        }

        chatRoomRepository.decreaseCurrentCount(companionId);

        // TODO: companion_participants.outcome_status 갱신

        return new ChatLeaveResponse(chatRoom.getId());
    }
}
