package com.ktb.moyeota.domain.community.service.cache;

import com.ktb.moyeota.domain.community.service.grid.GridCell;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Duration;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

@Slf4j
@Component
@RequiredArgsConstructor
public class MapPinCellCache {

    static final String KEY_PREFIX = "map-pins:cell:";
    static final Duration TTL = Duration.ofMinutes(1);
    static final String METRIC = "map_pins.cell_cache";

    private static final String READ = "read";
    private static final String WRITE = "write";
    private static final String EVICT = "evict";
    private static final String HIT = "hit";
    private static final String MISS = "miss";
    private static final String ERROR = "error";

    private final StringRedisTemplate redisTemplate;
    private final JsonMapper jsonMapper;
    private final MeterRegistry meterRegistry;

    public Optional<Map<GridCell, CellPins>> readCells(List<GridCell> cells) {
        if (cells.isEmpty()) {
            return Optional.of(Map.of());
        }
        List<String> values;
        try {
            values = redisTemplate.opsForValue().multiGet(keysOf(cells));
        } catch (DataAccessException e) {
            log.warn("[MAP_PIN_CACHE_READ_FAILED] Redis에서 칸을 읽지 못해 캐시 없이 조회합니다. cells={}", cells.size(), e);
            count(READ, ERROR, cells.size());
            return Optional.empty();
        }
        if (values == null) {
            count(READ, ERROR, cells.size());
            return Optional.empty();
        }

        Map<GridCell, CellPins> found = new LinkedHashMap<>();
        for (int i = 0; i < cells.size(); i++) {
            String value = values.get(i);
            if (value == null) {
                count(READ, MISS, 1);
                continue;
            }
            try {
                found.put(cells.get(i), jsonMapper.readValue(value, CellPins.class));
                count(READ, HIT, 1);
            } catch (JacksonException | IllegalArgumentException e) {
                log.warn("[MAP_PIN_CACHE_READ_FAILED] 캐시 값을 읽지 못해 캐시 미스로 처리합니다. key={}", keyOf(cells.get(i)), e);
                count(READ, ERROR, 1);
            }
        }
        return Optional.of(found);
    }

    public void writeCell(GridCell cell, CellPins pins) {
        String key = keyOf(cell);
        try {
            redisTemplate.opsForValue().set(key, jsonMapper.writeValueAsString(pins), TTL);
        } catch (JacksonException e) {
            log.error("[MAP_PIN_CACHE_WRITE_FAILED] 칸 값을 JSON으로 바꾸지 못했습니다. key={}", key, e);
            count(WRITE, ERROR, 1);
        } catch (DataAccessException e) {
            log.warn("[MAP_PIN_CACHE_WRITE_FAILED] Redis에 칸을 저장하지 못했습니다. key={}", key, e);
            count(WRITE, ERROR, 1);
        }
    }

    public void evictCells(Collection<GridCell> cells) {
        if (cells.isEmpty()) {
            return;
        }
        try {
            redisTemplate.delete(keysOf(cells));
        } catch (DataAccessException e) {
            log.warn("[MAP_PIN_CACHE_EVICT_FAILED] Redis에서 칸을 지우지 못했습니다. 최대 {} 뒤 만료됩니다. cells={}",
                    TTL, cells.size(), e);
            count(EVICT, ERROR, cells.size());
        }
    }

    private void count(String operation, String result, int amount) {
        meterRegistry.counter(METRIC, "operation", operation, "result", result).increment(amount);
    }

    static String keyOf(GridCell cell) {
        return KEY_PREFIX + cell.level() + ":" + cell.row() + ":" + cell.col();
    }

    private static List<String> keysOf(Collection<GridCell> cells) {
        return cells.stream().map(MapPinCellCache::keyOf).toList();
    }
}
