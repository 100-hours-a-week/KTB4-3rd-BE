package com.ktb.moyeota.domain.community.service.cache;

import com.ktb.moyeota.domain.community.dto.MapPinItem;
import com.ktb.moyeota.domain.community.dto.MapPinType;
import com.ktb.moyeota.domain.community.repository.CommunityPinProjection;
import com.ktb.moyeota.domain.community.repository.CommunityPostRepository;
import com.ktb.moyeota.domain.community.service.grid.CellBounds;
import com.ktb.moyeota.domain.community.service.grid.GridCell;
import com.ktb.moyeota.domain.community.service.grid.MapGrid;
import com.ktb.moyeota.domain.companionpost.repository.CompanionPinProjection;
import com.ktb.moyeota.domain.companionpost.repository.CompanionPostRepository;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class MapPinCellLoader {

    private static final int FETCH_LIMIT = CellPins.MAX_PINS + 1;

    private final MapGrid mapGrid;
    private final CommunityPostRepository communityPostRepository;
    private final CompanionPostRepository companionPostRepository;

    public CellPins load(GridCell cell) {
        CellBounds bounds = mapGrid.boundsOf(cell);
        List<CommunityPinProjection> communityPins = communityPostRepository.findPinsInCell(
                bounds.minLat(), bounds.minLng(), bounds.maxLat(), bounds.maxLng(), FETCH_LIMIT);
        if (communityPins.size() > CellPins.MAX_PINS) {
            return CellPins.overflowed();
        }
        List<CompanionPinProjection> companionPins = companionPostRepository.findPinsInCell(
                bounds.minLat(), bounds.minLng(), bounds.maxLat(), bounds.maxLng(), FETCH_LIMIT);
        if (communityPins.size() + companionPins.size() > CellPins.MAX_PINS) {
            return CellPins.overflowed();
        }

        List<MapPinItem> pins = new ArrayList<>(communityPins.size() + companionPins.size());
        for (CommunityPinProjection pin : communityPins) {
            pins.add(new MapPinItem(MapPinType.COMMUNITY, pin.getId(), pin.getLat(), pin.getLng()));
        }
        for (CompanionPinProjection pin : companionPins) {
            pins.add(new MapPinItem(MapPinType.COMPANION, pin.getId(), pin.getOriginLat(), pin.getOriginLng()));
        }
        return CellPins.of(pins);
    }
}
