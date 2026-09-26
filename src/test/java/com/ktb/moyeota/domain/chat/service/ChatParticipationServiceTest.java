package com.ktb.moyeota.domain.chat.service;

import static com.ktb.moyeota.fixture.CompanionFixture.companionPost;
import static com.ktb.moyeota.fixture.UserFixture.user;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.ktb.moyeota.domain.chat.dto.ChatLeaveResponse;
import com.ktb.moyeota.domain.chat.dto.ChatParticipateResponse;
import com.ktb.moyeota.domain.chat.entity.ChatRoom;
import com.ktb.moyeota.domain.chat.entity.CompanionParticipant;
import com.ktb.moyeota.domain.chat.entity.OutcomeStatus;
import com.ktb.moyeota.domain.chat.exception.ChatErrorCode;
import com.ktb.moyeota.domain.chat.repository.ChatRoomRepository;
import com.ktb.moyeota.domain.chat.repository.CompanionParticipantRepository;
import com.ktb.moyeota.domain.companion.entity.Companion;
import com.ktb.moyeota.domain.companion.entity.CompanionStatus;
import com.ktb.moyeota.domain.companionpost.repository.CompanionPostRepository;
import com.ktb.moyeota.domain.user.entity.User;
import com.ktb.moyeota.domain.user.repository.UserRepository;
import com.ktb.moyeota.global.exception.BusinessException;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class ChatParticipationServiceTest {

    private static final Long COMPANION_ID = 10L;
    private static final Long ROOM_ID = 501L;
    private static final Long HOST_ID = 1L;
    private static final Long GUEST_ID = 2L;

    @Mock
    private CompanionPostRepository companionPostRepository;

    @Mock
    private CompanionParticipantRepository companionParticipantRepository;

    @Mock
    private ChatRoomRepository chatRoomRepository;

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private ChatParticipationService service;

    private User host;
    private User guest;
    private Companion companion;
    private ChatRoom chatRoom;

    @BeforeEach
    void setUp() {
        host = user("호스트");
        ReflectionTestUtils.setField(host, "id", HOST_ID);
        guest = user("게스트");
        ReflectionTestUtils.setField(guest, "id", GUEST_ID);

        companion = companionPost(host);
        ReflectionTestUtils.setField(companion, "id", COMPANION_ID);

        chatRoom = ChatRoom.create(companion);
        ReflectionTestUtils.setField(chatRoom, "id", ROOM_ID);
    }

    @Nested
    @DisplayName("참여하기")
    class Participate {

        @Test
        @DisplayName("이미 참여 중이면 ALREADY_PARTICIPATING")
        void alreadyParticipating() {
            given(companionParticipantRepository.findActiveByCompanionIdAndUserId(COMPANION_ID, GUEST_ID))
                    .willReturn(Optional.of(CompanionParticipant.join(companion, guest)));

            assertThatThrownBy(() -> service.participate(GUEST_ID, COMPANION_ID))
                    .isInstanceOf(BusinessException.class)
                    .extracting(e -> ((BusinessException) e).getErrorCode())
                    .isEqualTo(ChatErrorCode.ALREADY_PARTICIPATING);
        }

        @Test
        @DisplayName("모집 마감이거나 정원이 찼으면(조건부 UPDATE 0건) COMPANION_NOT_JOINABLE")
        void notJoinable() {
            given(companionParticipantRepository.findActiveByCompanionIdAndUserId(COMPANION_ID, GUEST_ID))
                    .willReturn(Optional.empty());
            given(companionPostRepository.findById(COMPANION_ID)).willReturn(Optional.of(companion));
            given(userRepository.findById(GUEST_ID)).willReturn(Optional.of(guest));
            given(chatRoomRepository.increaseCurrentCountIfRecruitingAndNotFull(COMPANION_ID, CompanionStatus.RECRUITING))
                    .willReturn(0);

            assertThatThrownBy(() -> service.participate(GUEST_ID, COMPANION_ID))
                    .isInstanceOf(BusinessException.class)
                    .extracting(e -> ((BusinessException) e).getErrorCode())
                    .isEqualTo(ChatErrorCode.COMPANION_NOT_JOINABLE);
        }

        @Test
        @DisplayName("정상 참여하면 참여자 id와 채팅방 id를 반환한다")
        void participatesSuccessfully() {
            given(companionParticipantRepository.findActiveByCompanionIdAndUserId(COMPANION_ID, GUEST_ID))
                    .willReturn(Optional.empty());
            given(companionPostRepository.findById(COMPANION_ID)).willReturn(Optional.of(companion));
            given(userRepository.findById(GUEST_ID)).willReturn(Optional.of(guest));
            given(chatRoomRepository.increaseCurrentCountIfRecruitingAndNotFull(COMPANION_ID, CompanionStatus.RECRUITING))
                    .willReturn(1);

            CompanionParticipant saved = CompanionParticipant.join(companion, guest);
            ReflectionTestUtils.setField(saved, "id", 100L);
            given(companionParticipantRepository.save(any(CompanionParticipant.class))).willReturn(saved);
            given(chatRoomRepository.findByCompanionId(COMPANION_ID)).willReturn(Optional.of(chatRoom));

            ChatParticipateResponse response = service.participate(GUEST_ID, COMPANION_ID);

            assertThat(response.companionParticipantId()).isEqualTo(100L);
            assertThat(response.chatRoomId()).isEqualTo(ROOM_ID);
        }

        @Test
        @DisplayName("나갔던 동행에 다시 참여하면 새 참여 대신 예전 참여를 되살려 저장한다")
        void rejoinsPreviousParticipation() {
            given(companionParticipantRepository.findActiveByCompanionIdAndUserId(COMPANION_ID, GUEST_ID))
                    .willReturn(Optional.empty());
            given(companionPostRepository.findById(COMPANION_ID)).willReturn(Optional.of(companion));
            given(userRepository.findById(GUEST_ID)).willReturn(Optional.of(guest));
            given(chatRoomRepository.increaseCurrentCountIfRecruitingAndNotFull(COMPANION_ID, CompanionStatus.RECRUITING))
                    .willReturn(1);
            CompanionParticipant previous = CompanionParticipant.join(companion, guest);
            ReflectionTestUtils.setField(previous, "id", 100L);
            previous.leave();
            given(companionParticipantRepository.findByCompanionIdAndUserId(COMPANION_ID, GUEST_ID))
                    .willReturn(Optional.of(previous));
            given(companionParticipantRepository.save(previous)).willReturn(previous);
            given(chatRoomRepository.findByCompanionId(COMPANION_ID)).willReturn(Optional.of(chatRoom));

            ChatParticipateResponse response = service.participate(GUEST_ID, COMPANION_ID);

            assertThat(response.companionParticipantId()).isEqualTo(100L);
            assertThat(previous.getOutcomeStatus()).isEqualTo(OutcomeStatus.PENDING);
            assertThat(previous.getLeftAt()).isNull();
        }

        @Test
        @DisplayName("동시 요청으로 저장 시점에 UNIQUE 제약 위반이 나면 ALREADY_PARTICIPATING으로 응답한다")
        void raceConditionOnSave() {
            given(companionParticipantRepository.findActiveByCompanionIdAndUserId(COMPANION_ID, GUEST_ID))
                    .willReturn(Optional.empty());
            given(companionPostRepository.findById(COMPANION_ID)).willReturn(Optional.of(companion));
            given(userRepository.findById(GUEST_ID)).willReturn(Optional.of(guest));
            given(chatRoomRepository.increaseCurrentCountIfRecruitingAndNotFull(COMPANION_ID, CompanionStatus.RECRUITING))
                    .willReturn(1);
            given(companionParticipantRepository.save(any(CompanionParticipant.class)))
                    .willThrow(new DataIntegrityViolationException("uk_participants_companion_user"));

            assertThatThrownBy(() -> service.participate(GUEST_ID, COMPANION_ID))
                    .isInstanceOf(BusinessException.class)
                    .extracting(e -> ((BusinessException) e).getErrorCode())
                    .isEqualTo(ChatErrorCode.ALREADY_PARTICIPATING);
        }
    }

    @Nested
    @DisplayName("나가기")
    class Leave {

        @Test
        @DisplayName("참여 중이 아니면 NOT_PARTICIPATING")
        void notParticipating() {
            given(companionParticipantRepository.findActiveByCompanionIdAndUserId(COMPANION_ID, GUEST_ID))
                    .willReturn(Optional.empty());

            assertThatThrownBy(() -> service.leave(GUEST_ID, COMPANION_ID))
                    .isInstanceOf(BusinessException.class)
                    .extracting(e -> ((BusinessException) e).getErrorCode())
                    .isEqualTo(ChatErrorCode.NOT_PARTICIPATING);
        }

        @Test
        @DisplayName("방장이 아니면 위임 없이 그냥 나간다")
        void nonHostLeavesWithoutTransfer() {
            CompanionParticipant guestParticipation = CompanionParticipant.join(companion, guest);
            given(companionParticipantRepository.findActiveByCompanionIdAndUserId(COMPANION_ID, GUEST_ID))
                    .willReturn(Optional.of(guestParticipation));
            given(companionPostRepository.findById(COMPANION_ID)).willReturn(Optional.of(companion));
            given(chatRoomRepository.findByCompanionId(COMPANION_ID)).willReturn(Optional.of(chatRoom));

            ChatLeaveResponse response = service.leave(GUEST_ID, COMPANION_ID);

            assertThat(response.chatRoomId()).isEqualTo(ROOM_ID);
            assertThat(companion.getHost()).isEqualTo(host);
            assertThat(chatRoom.getClosedAt()).isNull();
            verify(companionParticipantRepository, never())
                    .findFirstByCompanionIdAndUserIdNotAndLeftAtIsNullOrderByJoinedAtAsc(any(), any());
        }

        @Test
        @DisplayName("방장이고 혼자 남았으면 채팅방을 닫는다")
        void closesRoomWhenHostAlone() {
            ReflectionTestUtils.setField(companion, "currentCount", 1);
            CompanionParticipant hostParticipation = CompanionParticipant.join(companion, host);
            given(companionParticipantRepository.findActiveByCompanionIdAndUserId(COMPANION_ID, HOST_ID))
                    .willReturn(Optional.of(hostParticipation));
            given(companionPostRepository.findById(COMPANION_ID)).willReturn(Optional.of(companion));
            given(chatRoomRepository.findByCompanionId(COMPANION_ID)).willReturn(Optional.of(chatRoom));

            service.leave(HOST_ID, COMPANION_ID);

            assertThat(chatRoom.getClosedAt()).isNotNull();
        }

        @Test
        @DisplayName("방장이고 다른 참여자가 있으면 가장 먼저 들어온 사람에게 위임된다")
        void transfersHostToEarliestJoiner() {
            ReflectionTestUtils.setField(companion, "currentCount", 2);
            CompanionParticipant hostParticipation = CompanionParticipant.join(companion, host);
            given(companionParticipantRepository.findActiveByCompanionIdAndUserId(COMPANION_ID, HOST_ID))
                    .willReturn(Optional.of(hostParticipation));
            given(companionPostRepository.findById(COMPANION_ID)).willReturn(Optional.of(companion));
            given(chatRoomRepository.findByCompanionId(COMPANION_ID)).willReturn(Optional.of(chatRoom));

            CompanionParticipant nextHostParticipation = CompanionParticipant.join(companion, guest);
            given(companionParticipantRepository
                    .findFirstByCompanionIdAndUserIdNotAndLeftAtIsNullOrderByJoinedAtAsc(COMPANION_ID, HOST_ID))
                    .willReturn(Optional.of(nextHostParticipation));

            service.leave(HOST_ID, COMPANION_ID);

            assertThat(companion.getHost()).isEqualTo(guest);
        }

        @Test
        @DisplayName("위임 대상을 못 찾으면 COMPANION_NEXT_HOST_NOT_FOUND(데이터 정합성 문제)")
        void nextHostNotFound() {
            ReflectionTestUtils.setField(companion, "currentCount", 2);
            CompanionParticipant hostParticipation = CompanionParticipant.join(companion, host);
            given(companionParticipantRepository.findActiveByCompanionIdAndUserId(COMPANION_ID, HOST_ID))
                    .willReturn(Optional.of(hostParticipation));
            given(companionPostRepository.findById(COMPANION_ID)).willReturn(Optional.of(companion));
            given(chatRoomRepository.findByCompanionId(COMPANION_ID)).willReturn(Optional.of(chatRoom));
            given(companionParticipantRepository
                    .findFirstByCompanionIdAndUserIdNotAndLeftAtIsNullOrderByJoinedAtAsc(COMPANION_ID, HOST_ID))
                    .willReturn(Optional.empty());

            assertThatThrownBy(() -> service.leave(HOST_ID, COMPANION_ID))
                    .isInstanceOf(BusinessException.class)
                    .extracting(e -> ((BusinessException) e).getErrorCode())
                    .isEqualTo(ChatErrorCode.COMPANION_NEXT_HOST_NOT_FOUND);
        }
    }
}
