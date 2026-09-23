package com.ktb.moyeota.domain.community.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

import com.ktb.moyeota.domain.community.dto.MapPinItem;
import com.ktb.moyeota.domain.community.dto.MapPinSearchRequest;
import com.ktb.moyeota.domain.community.dto.MapPinSearchResponse;
import com.ktb.moyeota.domain.community.dto.MapPinType;
import com.ktb.moyeota.domain.community.dto.NearbyPostCursor;
import com.ktb.moyeota.domain.community.dto.NearbyPostSearchRequest;
import com.ktb.moyeota.domain.community.dto.NearbyPostSearchResponse;
import com.ktb.moyeota.domain.community.repository.CommunityNearbyProjection;
import com.ktb.moyeota.domain.community.repository.CommunityPinProjection;
import com.ktb.moyeota.domain.community.repository.CommunityPostRepository;
import com.ktb.moyeota.domain.companionpost.repository.CompanionNearbyProjection;
import com.ktb.moyeota.domain.companionpost.repository.CompanionPinProjection;
import com.ktb.moyeota.domain.companionpost.repository.CompanionPostRepository;
import com.ktb.moyeota.domain.image.service.ImageUrlResolver;
import com.ktb.moyeota.global.external.s3.S3Properties;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class HomeServiceTest {

    private static final int PIN_LIMIT = 500;

    private final MapPinSearchRequest request = new MapPinSearchRequest(
            BigDecimal.valueOf(37.0), BigDecimal.valueOf(127.0),
            BigDecimal.valueOf(38.0), BigDecimal.valueOf(128.0));

    @Mock
    private CommunityPostRepository communityPostRepository;

    @Mock
    private CompanionPostRepository companionPostRepository;

    @Mock
    private NearbyPostCursorCodec cursorCodec;

    private HomeService service;

    @BeforeEach
    void setUp() {
        service = new HomeService(communityPostRepository, companionPostRepository, cursorCodec,
                new ImageUrlResolver(new S3Properties(
                        "moyeota-test-images", "ap-northeast-2", Duration.ofMinutes(5), "https://cdn.moyeota.test")));
    }

    private CommunityPinProjection communityPin(Long id) {
        CommunityPinProjection pin = mock(CommunityPinProjection.class);
        given(pin.getId()).willReturn(id);
        given(pin.getLat()).willReturn(BigDecimal.valueOf(37.5));
        given(pin.getLng()).willReturn(BigDecimal.valueOf(127.5));
        return pin;
    }

    private CompanionPinProjection companionPin(Long id) {
        CompanionPinProjection pin = mock(CompanionPinProjection.class);
        given(pin.getId()).willReturn(id);
        given(pin.getOriginLat()).willReturn(BigDecimal.valueOf(37.6));
        given(pin.getOriginLng()).willReturn(BigDecimal.valueOf(127.6));
        return pin;
    }

    @Test
    @DisplayName("커뮤니티 핀과 동행 핀을 하나의 목록으로 합친다")
    void mergesBothPinTypes() {
        CommunityPinProjection communityPin = communityPin(1L);
        CompanionPinProjection companionPin = companionPin(2L);
        given(communityPostRepository.findPinsInViewport(any(), any(), any(), any()))
                .willReturn(List.of(communityPin));
        given(companionPostRepository.findPinsInViewport(any(), any(), any(), any()))
                .willReturn(List.of(companionPin));

        MapPinSearchResponse response = service.searchMapPins(request);

        assertThat(response.limitExceeded()).isFalse();
        assertThat(response.items()).hasSize(2);
        MapPinItem communityItem = response.items().get(0);
        assertThat(communityItem.type()).isEqualTo(MapPinType.COMMUNITY);
        assertThat(communityItem.id()).isEqualTo(1L);
        MapPinItem companionItem = response.items().get(1);
        assertThat(companionItem.type()).isEqualTo(MapPinType.COMPANION);
        assertThat(companionItem.id()).isEqualTo(2L);
    }

    @Test
    @DisplayName("합산 결과가 500건 이하면 그대로 반환한다")
    void returnsItemsWhenUnderLimit() {
        CommunityPinProjection communityPin = communityPin(1L);
        given(communityPostRepository.findPinsInViewport(any(), any(), any(), any()))
                .willReturn(List.of(communityPin));
        given(companionPostRepository.findPinsInViewport(any(), any(), any(), any()))
                .willReturn(List.of());

        MapPinSearchResponse response = service.searchMapPins(request);

        assertThat(response.limitExceeded()).isFalse();
        assertThat(response.items()).hasSize(1);
    }

    @Test
    @DisplayName("합산 결과가 500건을 초과하면 빈 목록과 limitExceeded=true를 반환한다")
    void returnsEmptyWhenOverLimit() {
        List<CommunityPinProjection> manyPins = new ArrayList<>(PIN_LIMIT + 1);
        for (long id = 1; id <= PIN_LIMIT + 1; id++) {
            manyPins.add(mock(CommunityPinProjection.class)); // 내용은 중요하지 않음 — 개수만 검증
        }
        given(communityPostRepository.findPinsInViewport(any(), any(), any(), any())).willReturn(manyPins);
        given(companionPostRepository.findPinsInViewport(any(), any(), any(), any())).willReturn(List.of());

        MapPinSearchResponse response = service.searchMapPins(request);

        assertThat(response.limitExceeded()).isTrue();
        assertThat(response.items()).isEmpty();
    }

    @Test
    @DisplayName("주변 게시글의 작성자 프로필 이미지는 저장된 키가 아니라 공개 URL로 내려준다")
    void nearbyPostAuthorImageIsPublicUrl() {
        LocalDateTime createdAt = LocalDateTime.of(2026, 9, 23, 9, 0);
        CommunityNearbyProjection communityRow = mock(CommunityNearbyProjection.class);
        given(communityRow.getId()).willReturn(1L);
        given(communityRow.getTitle()).willReturn("제목");
        given(communityRow.getAuthorNickname()).willReturn("작성자");
        given(communityRow.getAuthorProfileImageUrl()).willReturn("profile/a.jpg");
        given(communityRow.getDistanceM()).willReturn(10.0);
        given(communityRow.getCommentCount()).willReturn(0);
        given(communityRow.getCreatedAt()).willReturn(createdAt);
        CompanionNearbyProjection companionRow = mock(CompanionNearbyProjection.class);
        given(companionRow.getId()).willReturn(2L);
        given(companionRow.getDistanceM()).willReturn(20.0);
        given(companionRow.getCreatedAt()).willReturn(createdAt);
        given(cursorCodec.decode(null)).willReturn(new NearbyPostCursor(null, null));
        given(communityPostRepository.findNearby(any(), any(), any(), any(), any(), any(), any(), any(), anyInt()))
                .willReturn(List.of(communityRow));
        given(companionPostRepository.findNearby(any(), any(), any(), any(), any(), any(), any(), any(), anyInt()))
                .willReturn(List.of(companionRow));

        NearbyPostSearchResponse response = service.searchNearbyPosts(new NearbyPostSearchRequest(
                BigDecimal.valueOf(37.5), BigDecimal.valueOf(127.5),
                BigDecimal.valueOf(37.0), BigDecimal.valueOf(127.0),
                BigDecimal.valueOf(38.0), BigDecimal.valueOf(128.0), null));

        assertThat(response.items()).extracting(item -> item.author().profileImageUrl())
                .containsExactly("https://cdn.moyeota.test/profile/a.jpg", null);
    }
}

