package com.ktb.moyeota.domain.chat.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

import com.ktb.moyeota.domain.chat.dto.ChatRoomDetailResponse;
import com.ktb.moyeota.domain.chat.dto.ChatRoomListResponse;
import com.ktb.moyeota.domain.chat.entity.ChatRoom;
import com.ktb.moyeota.domain.chat.entity.CompanionParticipant;
import com.ktb.moyeota.domain.chat.repository.ChatRoomListProjection;
import com.ktb.moyeota.domain.chat.repository.ChatRoomRepository;
import com.ktb.moyeota.domain.chat.repository.CompanionParticipantRepository;
import com.ktb.moyeota.domain.chat.repository.MessageRepository;
import com.ktb.moyeota.domain.companion.entity.Companion;
import com.ktb.moyeota.domain.companion.entity.CompanionKind;
import com.ktb.moyeota.domain.companion.entity.CompanionStatus;
import com.ktb.moyeota.domain.image.service.ImageUrlResolver;
import com.ktb.moyeota.domain.rating.repository.CompanionRatingRepository;
import com.ktb.moyeota.domain.settlement.entity.Settlement;
import com.ktb.moyeota.domain.settlement.entity.SettlementStatus;
import com.ktb.moyeota.domain.settlement.repository.SettlementRepository;
import com.ktb.moyeota.domain.user.entity.User;
import com.ktb.moyeota.global.external.s3.S3Properties;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ChatRoomServiceTest {

    private static final Long USER_ID = 7L;
    private static final Long ROOM_ID = 30L;
    private static final Long COMPANION_ID = 10L;

    @Mock
    private ChatRoomRepository chatRoomRepository;

    @Mock
    private CompanionParticipantRepository companionParticipantRepository;

    @Mock
    private MessageRepository messageRepository;

    @Mock
    private ChatRoomListProjection row;

    @Mock
    private SettlementRepository settlementRepository;

    @Mock
    private CompanionRatingRepository companionRatingRepository;

    private ChatRoomService service;

    @BeforeEach
    void setUp() {
        service = new ChatRoomService(chatRoomRepository, companionParticipantRepository, messageRepository,
                new ChatRoomCursorCodec(), new ImageUrlResolver(new S3Properties(
                        "moyeota-test-images", "ap-northeast-2", Duration.ofMinutes(5), "https://cdn.moyeota.test")),
                settlementRepository, companionRatingRepository);
    }

    @Test
    @DisplayName("채팅방 목록의 방장 프로필 이미지는 저장된 키를 URL로 바꿔 내린다")
    void resolvesHostProfileImageKeyToUrl() {
        given(row.getKind()).willReturn("TAXI_POT");
        given(row.getDepartureAt()).willReturn(LocalDateTime.of(2026, 9, 5, 8, 30));
        given(row.getHostProfileImageUrl()).willReturn("profile/host.png");
        given(chatRoomRepository.findMyChatRooms(eq(USER_ID), any(), any(), any(), anyInt()))
                .willReturn(List.of(row));

        ChatRoomListResponse response = service.findMyChatRooms(USER_ID, null, null);

        assertThat(response.items().get(0).host().profileImageUrl())
                .isEqualTo("https://cdn.moyeota.test/profile/host.png");
    }

    @Test
    @DisplayName("방장 프로필 이미지가 없으면 null로 내린다")
    void nullWhenHostHasNoImage() {
        given(row.getKind()).willReturn("TAXI_POT");
        given(row.getDepartureAt()).willReturn(LocalDateTime.of(2026, 9, 5, 8, 30));
        given(chatRoomRepository.findMyChatRooms(eq(USER_ID), any(), any(), any(), anyInt()))
                .willReturn(List.of(row));

        ChatRoomListResponse response = service.findMyChatRooms(USER_ID, null, null);

        assertThat(response.items().get(0).host().profileImageUrl()).isNull();
    }

    @Test
    @DisplayName("운행 완료 + 택시팟이면 settlement_status를 settlements 테이블 값으로 내린다")
    void settlementStatusForCompletedTaxiPot() {
        mockParticipant();
        Companion companion = mockCompanion(CompanionStatus.COMPLETED, CompanionKind.TAXI_POT);
        mockChatRoom(companion);

        Settlement settlement = mock(Settlement.class);
        given(settlement.getStatus()).willReturn(SettlementStatus.REQUESTED);
        given(settlementRepository.findWithPayeeByCompanionId(COMPANION_ID)).willReturn(Optional.of(settlement));

        ChatRoomDetailResponse response = service.findDetail(USER_ID, ROOM_ID);

        assertThat(response.settlementStatus()).isEqualTo(SettlementStatus.REQUESTED);
    }

    @Test
    @DisplayName("운행 완료 전이면 settlement_status는 null이고 settlements 테이블을 조회하지 않는다")
    void settlementStatusNullWhenNotCompleted() {
        mockParticipant();
        Companion companion = mockCompanion(CompanionStatus.RECRUITING, CompanionKind.TAXI_POT);
        mockChatRoom(companion);

        ChatRoomDetailResponse response = service.findDetail(USER_ID, ROOM_ID);

        assertThat(response.settlementStatus()).isNull();
        verifyNoInteractions(settlementRepository);
    }

    @Test
    @DisplayName("운행 완료라도 카풀·동행모집이면 settlement_status는 null이고 settlements 테이블을 조회하지 않는다")
    void settlementStatusNullWhenCompletedButNotTaxiPot() {
        mockParticipant();
        Companion companion = mockCompanion(CompanionStatus.COMPLETED, CompanionKind.CARPOOL);
        mockChatRoom(companion);

        ChatRoomDetailResponse response = service.findDetail(USER_ID, ROOM_ID);

        assertThat(response.settlementStatus()).isNull();
        verifyNoInteractions(settlementRepository);
    }

    private void mockParticipant() {
        CompanionParticipant participant = mock(CompanionParticipant.class);
        given(participant.getMessage()).willReturn(null);
        given(companionParticipantRepository.findActiveByChatRoomIdAndUserId(ROOM_ID, USER_ID))
                .willReturn(Optional.of(participant));
    }

    private Companion mockCompanion(CompanionStatus status, CompanionKind kind) {
        User host = mock(User.class);
        given(host.getId()).willReturn(USER_ID);

        Companion companion = mock(Companion.class);
        given(companion.getId()).willReturn(COMPANION_ID);
        given(companion.getKind()).willReturn(kind);
        given(companion.getStatus()).willReturn(status);
        given(companion.getHost()).willReturn(host);
        given(companion.getOriginName()).willReturn("판교역");
        given(companion.getDestName()).willReturn("강남역");
        given(companion.getDepartureAt()).willReturn(LocalDateTime.of(2026, 9, 5, 8, 30));
        given(companion.getCurrentCount()).willReturn(3);
        given(companion.getCapacity()).willReturn(4);
        return companion;
    }

    private void mockChatRoom(Companion companion) {
        ChatRoom chatRoom = mock(ChatRoom.class);
        given(chatRoom.getId()).willReturn(ROOM_ID);
        given(chatRoom.getCompanion()).willReturn(companion);
        given(chatRoom.getClosedAt()).willReturn(null);
        given(chatRoomRepository.findByIdWithCompanion(ROOM_ID)).willReturn(Optional.of(chatRoom));
    }
}
