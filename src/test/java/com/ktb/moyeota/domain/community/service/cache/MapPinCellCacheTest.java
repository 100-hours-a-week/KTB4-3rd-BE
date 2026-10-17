package com.ktb.moyeota.domain.community.service.cache;

import static org.assertj.core.api.Assertions.assertThat;

import com.ktb.moyeota.domain.community.dto.MapPinItem;
import com.ktb.moyeota.domain.community.dto.MapPinType;
import com.ktb.moyeota.domain.community.service.grid.GridCell;
import com.ktb.moyeota.global.config.RedisTestContainerConfig;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.redis.test.autoconfigure.DataRedisTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.core.StringRedisTemplate;
import tools.jackson.databind.PropertyNamingStrategies;
import tools.jackson.databind.json.JsonMapper;

@DataRedisTest
@Import(RedisTestContainerConfig.class)
class MapPinCellCacheTest {

    private static final GridCell GANGNAM = new GridCell(0, 3749, 12702);
    private static final GridCell NEXT_TO_GANGNAM = new GridCell(0, 3749, 12703);
    private static final GridCell EMPTY_AREA = new GridCell(1, 1874, 6351);

    private final JsonMapper jsonMapper = JsonMapper.builder()
            .propertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE)
            .build();

    @Autowired
    private StringRedisTemplate redisTemplate;

    private SimpleMeterRegistry meterRegistry;
    private MapPinCellCache cache;

    @BeforeEach
    void setUp() {
        Set<String> keys = redisTemplate.keys(MapPinCellCache.KEY_PREFIX + "*");
        if (keys != null && !keys.isEmpty()) {
            redisTemplate.delete(keys);
        }
        meterRegistry = new SimpleMeterRegistry();
        cache = new MapPinCellCache(redisTemplate, jsonMapper, meterRegistry);
    }

    @Test
    @DisplayName("저장한 칸을 좌표 자릿수까지 그대로 읽는다")
    void readsWhatWasWritten() {
        CellPins written = CellPins.of(List.of(
                new MapPinItem(MapPinType.COMMUNITY, 1L, new BigDecimal("37.497912"), new BigDecimal("127.027600")),
                new MapPinItem(MapPinType.COMPANION, 2L, new BigDecimal("37.498000"), new BigDecimal("127.028100"))));

        cache.writeCell(GANGNAM, written);

        assertThat(cache.readCells(List.of(GANGNAM))).containsExactly(Map.entry(GANGNAM, written));
    }

    @Test
    @DisplayName("키는 map-pins:cell:{단계}:{행}:{열} 형식이다")
    void keyFormat() {
        cache.writeCell(GANGNAM, CellPins.of(List.of()));

        assertThat(redisTemplate.hasKey("map-pins:cell:0:3749:12702")).isTrue();
        assertThat(MapPinCellCache.keyOf(new GridCell(2, -5, -12))).isEqualTo("map-pins:cell:2:-5:-12");
    }

    @Test
    @DisplayName("핀이 없는 칸도 저장해 캐시 미스와 구분한다")
    void emptyCellIsHit() {
        cache.writeCell(EMPTY_AREA, CellPins.of(List.of()));

        Map<GridCell, CellPins> found = cache.readCells(List.of(EMPTY_AREA));

        assertThat(found).containsKey(EMPTY_AREA);
        assertThat(found.get(EMPTY_AREA).pins()).isEmpty();
        assertThat(found.get(EMPTY_AREA).overflow()).isFalse();
    }

    @Test
    @DisplayName("저장하지 않은 칸은 결과에 담지 않는다")
    void missingCellIsLeftOut() {
        cache.writeCell(GANGNAM, CellPins.of(List.of()));

        Map<GridCell, CellPins> found = cache.readCells(List.of(GANGNAM, NEXT_TO_GANGNAM));

        assertThat(found).containsOnlyKeys(GANGNAM);
    }

    @Test
    @DisplayName("넘친 칸은 넘침 표시로 읽는다")
    void overflowedCell() {
        cache.writeCell(GANGNAM, CellPins.overflowed());

        CellPins found = cache.readCells(List.of(GANGNAM)).get(GANGNAM);

        assertThat(found.overflow()).isTrue();
        assertThat(found.pins()).isEmpty();
    }

    @Test
    @DisplayName("저장한 칸은 1분 뒤 만료된다")
    void expiresInOneMinute() {
        cache.writeCell(GANGNAM, CellPins.of(List.of()));

        assertThat(redisTemplate.getExpire(MapPinCellCache.keyOf(GANGNAM))).isBetween(1L, 60L);
    }

    @Test
    @DisplayName("지운 칸은 다시 읽으면 캐시 미스다")
    void evictedCellIsMiss() {
        cache.writeCell(GANGNAM, CellPins.of(List.of()));
        cache.writeCell(NEXT_TO_GANGNAM, CellPins.of(List.of()));

        cache.evictCells(List.of(GANGNAM, NEXT_TO_GANGNAM));

        assertThat(cache.readCells(List.of(GANGNAM, NEXT_TO_GANGNAM))).isEmpty();
    }

    @Test
    @DisplayName("읽은 칸마다 히트와 미스를 센다")
    void countsHitAndMiss() {
        cache.writeCell(GANGNAM, CellPins.of(List.of()));

        cache.readCells(List.of(GANGNAM, NEXT_TO_GANGNAM, EMPTY_AREA));

        assertThat(count("read", "hit")).isEqualTo(1.0);
        assertThat(count("read", "miss")).isEqualTo(2.0);
    }

    @Test
    @DisplayName("빈 칸 목록은 Redis에 묻지 않고 빈 결과를 돌려준다")
    void emptyRequest() {
        assertThat(cache.readCells(List.of())).isEmpty();
        cache.evictCells(List.of());

        assertThat(meterRegistry.find(MapPinCellCache.METRIC).counters()).isEmpty();
    }

    private double count(String operation, String result) {
        return meterRegistry.counter(MapPinCellCache.METRIC, "operation", operation, "result", result).count();
    }
}
