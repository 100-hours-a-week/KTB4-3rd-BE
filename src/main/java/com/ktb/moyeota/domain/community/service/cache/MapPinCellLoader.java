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
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class MapPinCellLoader {

    private static final int FETCH_LIMIT = CellPins.MAX_PINS + 1;

    private final MapGrid mapGrid;
    private final CommunityPostRepository communityPostRepository;
    private final CompanionPostRepository companionPostRepository;
    private final MapPinCellCache mapPinCellCache;
    private final ConcurrentMap<GridCell, CompletableFuture<CellPins>> inFlight = new ConcurrentHashMap<>();

    public CellPins load(GridCell cell) {
        CompletableFuture<CellPins> mine = new CompletableFuture<>();
        CompletableFuture<CellPins> running = inFlight.putIfAbsent(cell, mine);
        if (running != null) {
            return awaitResult(running);
        }
        try {
            CellPins pins = cachedOrQueried(cell);
            mine.complete(pins);
            return pins;
        } catch (RuntimeException e) {
            mine.completeExceptionally(e);
            throw e;
        } finally {
            inFlight.remove(cell, mine);
        }
    }

    private CellPins cachedOrQueried(GridCell cell) {
        Optional<CellPins> cached = mapPinCellCache.recheckCell(cell);
        if (cached.isPresent()) {
            return cached.get();
        }
        CellPins pins = query(cell);
        mapPinCellCache.writeCell(cell, pins);
        return pins;
    }

    private CellPins query(GridCell cell) {
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

    private static CellPins awaitResult(CompletableFuture<CellPins> running) {
        try {
            return running.join();
        } catch (CompletionException e) {
            if (e.getCause() instanceof RuntimeException cause) {
                throw cause;
            }
            throw e;
        }
    }
}
