package com.ktb.moyeota.domain.community.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

import com.ktb.moyeota.domain.community.dto.MapPinItem;
import com.ktb.moyeota.domain.community.dto.MapPinSearchRequest;
import com.ktb.moyeota.domain.community.dto.MapPinSearchResponse;
import com.ktb.moyeota.domain.community.dto.MapPinType;
import com.ktb.moyeota.domain.community.repository.CommunityPinProjection;
import com.ktb.moyeota.domain.community.repository.CommunityPostRepository;
import com.ktb.moyeota.domain.companionpost.repository.CompanionPinProjection;
import com.ktb.moyeota.domain.companionpost.repository.CompanionPostRepository;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * HomeService 단위 테스트 (GET /map-pins).
 *
 * [참고] findPinsInViewport() native query 자체(뷰포트 필터, deleted_at 조건 등)는 여기서
 * 검증하지 않는다 — Repository가 반환한 결과를 HomeService가 어떻게 합치고 limit을 적용하는지만
 * 검증한다. 500건 초과 시 DB 단이 아니라 애플리케이션 레벨에서만 잘라내는 현재 구조의 한계는
 * TODO로 별도 기록해뒀다.
 */
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
        service = new HomeService(communityPostRepository, companionPostRepository, cursorCodec);
    }

    private CommunityPinProjection communityPin(Long id, String title) {
        CommunityPinProjection pin = mock(CommunityPinProjection.class);
        given(pin.getId()).willReturn(id);
        given(pin.getTitle()).willReturn(title);
        given(pin.getLat()).willReturn(BigDecimal.valueOf(37.5));
        given(pin.getLng()).willReturn(BigDecimal.valueOf(127.5));
        return pin;
    }

    private CompanionPinProjection companionPin(Long id) {
        CompanionPinProjection pin = mock(CompanionPinProjection.class);
        given(pin.getId()).willReturn(id);
        given(pin.getOriginName()).willReturn("판교역");
        given(pin.getDestName()).willReturn("강남역");
        given(pin.getOriginLat()).willReturn(BigDecimal.valueOf(37.6));
        given(pin.getOriginLng()).willReturn(BigDecimal.valueOf(127.6));
        return pin;
    }

    @Test
    @DisplayName("커뮤니티 핀과 동행 핀을 하나의 목록으로 합친다")
    void mergesBothPinTypes() {
        given(communityPostRepository.findPinsInViewport(any(), any(), any(), any()))
                .willReturn(List.of(communityPin(1L, "판교역 근처 카페 추천")));
        given(companionPostRepository.findPinsInViewport(any(), any(), any(), any()))
                .willReturn(List.of(companionPin(2L)));

        MapPinSearchResponse response = service.searchMapPins(request);

        assertThat(response.limitExceeded()).isFalse();
        assertThat(response.items()).hasSize(2);
        MapPinItem communityItem = response.items().get(0);
        assertThat(communityItem.type()).isEqualTo(MapPinType.COMMUNITY);
        assertThat(communityItem.id()).isEqualTo(1L);
        assertThat(communityItem.title()).isEqualTo("판교역 근처 카페 추천");
        MapPinItem companionItem = response.items().get(1);
        assertThat(companionItem.type()).isEqualTo(MapPinType.COMPANION);
        assertThat(companionItem.id()).isEqualTo(2L);
        // 동행모집 핀 title은 "출발지 → 도착지" 형태로 조립된다.
        assertThat(companionItem.title()).isEqualTo("판교역 → 강남역");
    }

    @Test
    @DisplayName("합산 결과가 500건 이하면 그대로 반환한다")
    void returnsItemsWhenUnderLimit() {
        given(communityPostRepository.findPinsInViewport(any(), any(), any(), any()))
                .willReturn(List.of(communityPin(1L, "글")));
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
}
