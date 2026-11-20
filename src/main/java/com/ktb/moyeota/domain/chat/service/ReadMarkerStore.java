package com.ktb.moyeota.domain.chat.service;

import com.ktb.moyeota.domain.chat.dto.ReadMarkerRequest;
import com.ktb.moyeota.domain.chat.dto.ReadMarkerResponse;
import com.ktb.moyeota.domain.chat.entity.CompanionParticipant;

public interface ReadMarkerStore {

    ReadMarkerResponse markRead(CompanionParticipant participant, Long roomId, ReadMarkerRequest request);

    ReadMarkerResponse current(CompanionParticipant participant);
}
