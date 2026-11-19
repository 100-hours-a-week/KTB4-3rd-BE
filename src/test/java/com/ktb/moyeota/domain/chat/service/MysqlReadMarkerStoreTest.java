package com.ktb.moyeota.domain.chat.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.ktb.moyeota.domain.chat.dto.ReadMarkerRequest;
import com.ktb.moyeota.domain.chat.dto.ReadMarkerResponse;
import com.ktb.moyeota.domain.chat.entity.CompanionParticipant;
import com.ktb.moyeota.domain.chat.entity.Message;
import com.ktb.moyeota.domain.chat.repository.CompanionParticipantRepository;
import com.ktb.moyeota.domain.chat.repository.MessageRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class MysqlReadMarkerStoreTest {

    private static final Long ROOM_ID = 30L;
    private static final Long PARTICIPANT_ID = 10L;

    @Mock
    private CompanionParticipantRepository companionParticipantRepository;

    @Mock
    private MessageRepository messageRepository;

    @Mock
    private CompanionParticipant participant;

    private MysqlReadMarkerStore store;

    @BeforeEach
    void setUp() {
        store = new MysqlReadMarkerStore(companionParticipantRepository, messageRepository);
    }

    @Test
    @DisplayName("더 최신 메시지 id면 갱신하고 그 id를 내린다")
    void updatesWhenNewer() {
        Message message = mock(Message.class);
        given(participant.getId()).willReturn(PARTICIPANT_ID);
        given(messageRepository.getReferenceById(55L)).willReturn(message);
        given(companionParticipantRepository.updateLastReadMessageIfNewer(PARTICIPANT_ID, message, 55L))
                .willReturn(1);

        ReadMarkerResponse response = store.markRead(participant, ROOM_ID, new ReadMarkerRequest(55L, null));

        assertThat(response).isEqualTo(new ReadMarkerResponse(55L, null));
    }

    @Test
    @DisplayName("갱신되지 않으면 원래 읽음 위치를 내린다")
    void keepsCurrentWhenNotNewer() {
        Message message = mock(Message.class);
        Message current = mock(Message.class);
        given(current.getId()).willReturn(60L);
        given(participant.getId()).willReturn(PARTICIPANT_ID);
        given(participant.getMessage()).willReturn(current);
        given(messageRepository.getReferenceById(55L)).willReturn(message);
        given(companionParticipantRepository.updateLastReadMessageIfNewer(PARTICIPANT_ID, message, 55L))
                .willReturn(0);

        ReadMarkerResponse response = store.markRead(participant, ROOM_ID, new ReadMarkerRequest(55L, null));

        assertThat(response).isEqualTo(new ReadMarkerResponse(60L, null));
    }

    @Test
    @DisplayName("메시지 id가 없으면 갱신하지 않고 원래 읽음 위치를 내린다")
    void keepsCurrentWhenNoMessageId() {
        ReadMarkerResponse response = store.markRead(participant, ROOM_ID, new ReadMarkerRequest(null, 3L));

        assertThat(response).isEqualTo(new ReadMarkerResponse(null, null));
        verify(companionParticipantRepository, never()).updateLastReadMessageIfNewer(anyLong(), any(), anyLong());
    }
}
