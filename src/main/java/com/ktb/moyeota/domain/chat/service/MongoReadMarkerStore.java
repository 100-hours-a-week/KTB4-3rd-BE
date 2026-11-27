package com.ktb.moyeota.domain.chat.service;

import com.ktb.moyeota.domain.chat.dto.ReadMarkerRequest;
import com.ktb.moyeota.domain.chat.dto.ReadMarkerResponse;
import com.ktb.moyeota.domain.chat.entity.CompanionParticipant;
import com.ktb.moyeota.domain.chat.exception.ChatErrorCode;
import com.ktb.moyeota.domain.chat.repository.ChatMessageRepository;
import com.ktb.moyeota.domain.chat.repository.CompanionParticipantRepository;
import com.ktb.moyeota.global.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "moyeota.chat", name = "message-store", havingValue = "mongo")
public class MongoReadMarkerStore implements ReadMarkerStore {

    private final CompanionParticipantRepository companionParticipantRepository;
    private final ChatMessageRepository chatMessageRepository;

    @Override
    public ReadMarkerResponse markRead(CompanionParticipant participant, Long roomId, ReadMarkerRequest request) {
        Long requested = request.lastReadSeq();
        if (requested == null) {
            return current(participant);
        }
        if (requested < 1 || requested > chatMessageRepository.lastSeq(roomId)) {
            throw new BusinessException(ChatErrorCode.INVALID_LAST_READ_SEQ);
        }
        int updatedRows = companionParticipantRepository.updateLastReadSeqIfNewer(participant.getId(), requested);
        if (updatedRows > 0) {
            return new ReadMarkerResponse(null, requested);
        }
        return current(participant);
    }

    @Override
    public ReadMarkerResponse current(CompanionParticipant participant) {
        return new ReadMarkerResponse(null, participant.getLastReadSeq());
    }
}
