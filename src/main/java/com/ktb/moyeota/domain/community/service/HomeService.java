package com.ktb.moyeota.domain.community.service;

import com.ktb.moyeota.domain.community.dto.MapPinItem;
import com.ktb.moyeota.domain.community.dto.MapPinSearchRequest;
import com.ktb.moyeota.domain.community.dto.MapPinSearchResponse;
import com.ktb.moyeota.domain.community.dto.MapPinType;
import com.ktb.moyeota.domain.community.repository.CommunityPinProjection;
import com.ktb.moyeota.domain.community.repository.CommunityPostRepository;
import com.ktb.moyeota.domain.companionpost.repository.CompanionPinProjection;
import com.ktb.moyeota.domain.companionpost.repository.CompanionPostRepository;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 네비게이션 바 '홈' 전용 서비스 — GET /map-pins (나중에 GET /nearby-posts도 여기서 처리 예정,
 * 테크스펙 지시대로 getMapPins/getNearbyPosts를 같은 HomeService가 담당).
 *
 * [주의할 내용] CompanionPostRepository(domain/companionpost, COMPANION 도메인 소관)를
 * 직접 주입받아 쓴다 — 테크스펙 296~302번째 줄에 명시된 대로 Port/Adapter 없이 직접 의존.
 * HomeRepository는 테크스펙에 "나중에 조인해서 쓸 수 있으므로 빈 클래스로만 만든다"고
 * 돼 있는데, 지금 이 메서드는 HomeRepository 없이 CommunityPostRepository +
 * CompanionPostRepository를 각각 직접 호출하는 것만으로 충분해서 HomeRepository는 아직
 * 만들지 않았다 — 필요해지면 그때 빈 클래스로 추가하면 될 것 같은데, 지금 만들어둘지 확인해줘.
 */
@Service
@RequiredArgsConstructor
public class HomeService {

    private static final int PIN_LIMIT = 500;

    private final CommunityPostRepository communityPostRepository;
    private final CompanionPostRepository companionPostRepository;

    @Transactional(readOnly = true)
    public MapPinSearchResponse search(MapPinSearchRequest request) {
        List<CommunityPinProjection> communityPins = communityPostRepository.findPinsInViewport(
                request.swLat(), request.swLng(), request.neLat(), request.neLng());
        List<CompanionPinProjection> companionPins = companionPostRepository.findPinsInViewport(
                request.swLat(), request.swLng(), request.neLat(), request.neLng());

        List<MapPinItem> items = new ArrayList<>(communityPins.size() + companionPins.size());
        for (CommunityPinProjection pin : communityPins) {
            items.add(new MapPinItem(MapPinType.COMMUNITY, pin.getId(), pin.getTitle(), pin.getLat(), pin.getLng()));
        }
        for (CompanionPinProjection pin : companionPins) {
            // CompanionPostDetailResponse.of()와 동일한 "출발지 → 도착지" 조립 규칙을 재사용
            String title = pin.getOriginName() + " → " + pin.getDestName();
            items.add(new MapPinItem(MapPinType.COMPANION, pin.getId(), title, pin.getOriginLat(), pin.getOriginLng()));
        }

        if (items.size() > PIN_LIMIT) {
            return new MapPinSearchResponse(List.of(), true);
        }
        return new MapPinSearchResponse(items, false);
    }
}
