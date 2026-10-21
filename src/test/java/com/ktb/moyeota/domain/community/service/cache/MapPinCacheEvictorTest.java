package com.ktb.moyeota.domain.community.service.cache;

import static org.mockito.Mockito.verify;

import com.ktb.moyeota.domain.community.event.MapPinChangedEvent;
import com.ktb.moyeota.domain.community.service.grid.GridCell;
import com.ktb.moyeota.domain.community.service.grid.MapGrid;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class MapPinCacheEvictorTest {

    @Mock
    private MapPinCellCache mapPinCellCache;

    private MapPinCacheEvictor evictor;

    @BeforeEach
    void setUp() {
        evictor = new MapPinCacheEvictor(new MapGrid(10_000, 6, 4), mapPinCellCache);
    }

    @Test
    @DisplayName("핀 좌표가 속한 칸을 단계마다 하나씩, 6칸 지운다")
    void evictsCellOfEveryLevel() {
        evictor.evict(new MapPinChangedEvent(new BigDecimal("37.497942"), new BigDecimal("127.027621")));

        verify(mapPinCellCache).evictCells(List.of(
                new GridCell(0, 3749, 12702),
                new GridCell(1, 1874, 6351),
                new GridCell(2, 937, 3175),
                new GridCell(3, 468, 1587),
                new GridCell(4, 234, 793),
                new GridCell(5, 117, 396)));
    }
}
