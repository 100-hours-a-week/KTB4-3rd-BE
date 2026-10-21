package com.ktb.moyeota.domain.community.service.cache;

import com.ktb.moyeota.domain.community.event.MapPinChangedEvent;
import com.ktb.moyeota.domain.community.service.grid.MapGrid;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
@RequiredArgsConstructor
public class MapPinCacheEvictor {

    private final MapGrid mapGrid;
    private final MapPinCellCache mapPinCellCache;

    @TransactionalEventListener
    public void evict(MapPinChangedEvent event) {
        mapPinCellCache.evictCells(mapGrid.cellsContaining(event.lat(), event.lng()));
    }
}
