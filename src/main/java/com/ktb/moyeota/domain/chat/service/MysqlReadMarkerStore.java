package com.ktb.moyeota.domain.chat.service;

import com.ktb.moyeota.domain.chat.dto.ReadMarkerRequest;
import com.ktb.moyeota.domain.chat.dto.ReadMarkerResponse;
import com.ktb.moyeota.domain.chat.entity.CompanionParticipant;
import com.ktb.moyeota.domain.chat.entity.Message;
import com.ktb.moyeota.domain.chat.repository.CompanionParticipantRepository;
import com.ktb.moyeota.domain.chat.repository.MessageRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "moyeota.chat", name = "message-store", havingValue = "mysql", matchIfMissing = true)
public class MysqlReadMarkerStore implements ReadMarkerStore {

    private final CompanionParticipantRepository companionParticipantRepository;
    private final MessageRepository messageRepository;

    @Override
    public ReadMarkerResponse markRead(CompanionParticipant participant, Long roomId, ReadMarkerRequest request) {
        Long requested = request.lastReadMessageId();
        if (requested != null) {
            Message messageRef = messageRepository.getReferenceById(requested);
            int updatedRows = companionParticipantRepository.updateLastReadMessageIfNewer(
                    participant.getId(), messageRef, requested);
            if (updatedRows > 0) {
                return new ReadMarkerResponse(requested, null);
            }
        }
        return current(participant);
    }

    @Override
    public ReadMarkerResponse current(CompanionParticipant participant) {
        Message message = participant.getMessage();
        return new ReadMarkerResponse(message != null ? message.getId() : null, null);
    }
}
