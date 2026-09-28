package com.ktb.moyeota.domain.chat.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;

import com.ktb.moyeota.domain.chat.dto.ChatRoomListResponse;
import com.ktb.moyeota.domain.chat.repository.ChatRoomListProjection;
import com.ktb.moyeota.domain.chat.repository.ChatRoomRepository;
import com.ktb.moyeota.domain.chat.repository.CompanionParticipantRepository;
import com.ktb.moyeota.domain.chat.repository.MessageRepository;
import com.ktb.moyeota.domain.image.service.ImageUrlResolver;
import com.ktb.moyeota.global.external.s3.S3Properties;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ChatRoomServiceTest {

    private static final Long USER_ID = 7L;

    @Mock
    private ChatRoomRepository chatRoomRepository;

    @Mock
    private CompanionParticipantRepository companionParticipantRepository;

    @Mock
    private MessageRepository messageRepository;

    @Mock
    private ChatRoomListProjection row;

    private ChatRoomService service;

    @BeforeEach
    void setUp() {
        service = new ChatRoomService(chatRoomRepository, companionParticipantRepository, messageRepository,
                new ChatRoomCursorCodec(), new ImageUrlResolver(new S3Properties(
                        "moyeota-test-images", "ap-northeast-2", Duration.ofMinutes(5), "https://cdn.moyeota.test")));
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
}
