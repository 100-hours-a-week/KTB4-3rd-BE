package com.ktb.moyeota.domain.chat.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.ktb.moyeota.domain.chat.dto.ReadMarkerRequest;
import com.ktb.moyeota.domain.chat.dto.ReadMarkerResponse;
import com.ktb.moyeota.domain.chat.entity.CompanionParticipant;
import com.ktb.moyeota.domain.chat.exception.ChatErrorCode;
import com.ktb.moyeota.domain.chat.repository.ChatMessageRepository;
import com.ktb.moyeota.domain.chat.repository.CompanionParticipantRepository;
import com.ktb.moyeota.global.exception.BusinessException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class MongoReadMarkerStoreTest {

    private static final Long ROOM_ID = 30L;
    private static final Long PARTICIPANT_ID = 10L;
    private static final long LAST_SEQ = 57L;

    @Mock
    private CompanionParticipantRepository companionParticipantRepository;

    @Mock
    private ChatMessageRepository chatMessageRepository;

    @Mock
    private CompanionParticipant participant;

    private MongoReadMarkerStore store;

    @BeforeEach
    void setUp() {
        store = new MongoReadMarkerStore(companionParticipantRepository, chatMessageRepository);
    }

    @Test
    @DisplayName("방에 있는 더 큰 번호면 갱신하고 그 번호를 내린다")
    void updatesWhenNewer() {
        given(chatMessageRepository.lastSeq(ROOM_ID)).willReturn(LAST_SEQ);
        given(participant.getId()).willReturn(PARTICIPANT_ID);
        given(companionParticipantRepository.updateLastReadSeqIfNewer(PARTICIPANT_ID, LAST_SEQ)).willReturn(1);

        ReadMarkerResponse response = store.markRead(participant, ROOM_ID, new ReadMarkerRequest(null, LAST_SEQ));

        assertThat(response).isEqualTo(new ReadMarkerResponse(null, LAST_SEQ));
    }

    @Test
    @DisplayName("갱신되지 않으면(이미 더 뒤를 읽음) 원래 읽음 위치를 내린다")
    void keepsCurrentWhenNotNewer() {
        given(chatMessageRepository.lastSeq(ROOM_ID)).willReturn(LAST_SEQ);
        given(participant.getId()).willReturn(PARTICIPANT_ID);
        given(participant.getLastReadSeq()).willReturn(50L);
        given(companionParticipantRepository.updateLastReadSeqIfNewer(PARTICIPANT_ID, 45L)).willReturn(0);

        ReadMarkerResponse response = store.markRead(participant, ROOM_ID, new ReadMarkerRequest(null, 45L));

        assertThat(response).isEqualTo(new ReadMarkerResponse(null, 50L));
    }

    @Test
    @DisplayName("번호가 없으면 갱신하지 않고 원래 읽음 위치를 내린다")
    void keepsCurrentWhenNoSeq() {
        given(participant.getLastReadSeq()).willReturn(50L);

        ReadMarkerResponse response = store.markRead(participant, ROOM_ID, new ReadMarkerRequest(55L, null));

        assertThat(response).isEqualTo(new ReadMarkerResponse(null, 50L));
        verifyNoInteractions(chatMessageRepository, companionParticipantRepository);
    }

    @ParameterizedTest
    @ValueSource(longs = {0L, -3L})
    @DisplayName("1보다 작은 번호는 INVALID_LAST_READ_SEQ 로 거절한다")
    void rejectsNonPositive(long seq) {
        assertThatThrownBy(() -> store.markRead(participant, ROOM_ID, new ReadMarkerRequest(null, seq)))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ChatErrorCode.INVALID_LAST_READ_SEQ);
        verify(companionParticipantRepository, never()).updateLastReadSeqIfNewer(anyLong(), anyLong());
    }

    @Test
    @DisplayName("방의 마지막 번호보다 큰 번호는 INVALID_LAST_READ_SEQ 로 거절한다")
    void rejectsBeyondLastSeq() {
        given(chatMessageRepository.lastSeq(ROOM_ID)).willReturn(LAST_SEQ);

        assertThatThrownBy(() -> store.markRead(participant, ROOM_ID, new ReadMarkerRequest(null, LAST_SEQ + 1)))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ChatErrorCode.INVALID_LAST_READ_SEQ);
        verify(companionParticipantRepository, never()).updateLastReadSeqIfNewer(anyLong(), anyLong());
    }

    @Test
    @DisplayName("현재 읽음 위치는 last_read_seq 만 내린다")
    void currentReturnsSeqOnly() {
        given(participant.getLastReadSeq()).willReturn(12L);

        assertThat(store.current(participant)).isEqualTo(new ReadMarkerResponse(null, 12L));
    }
}
