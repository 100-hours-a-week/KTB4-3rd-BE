package com.ktb.moyeota.domain.community.service.cache;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;

import com.ktb.moyeota.domain.community.service.grid.GridCell;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.QueryTimeoutException;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import tools.jackson.databind.PropertyNamingStrategies;
import tools.jackson.databind.json.JsonMapper;

@ExtendWith(MockitoExtension.class)
class MapPinCellCacheFailureTest {

    private static final List<GridCell> CELLS = List.of(new GridCell(0, 3749, 12702), new GridCell(0, 3749, 12703));

    private final JsonMapper jsonMapper = JsonMapper.builder()
            .propertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE)
            .build();
    private final SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    private MapPinCellCache cache;

    @BeforeEach
    void setUp() {
        cache = new MapPinCellCache(redisTemplate, jsonMapper, meterRegistry);
    }

    @Test
    @DisplayName("Redis에 연결하지 못하면 예외 없이 모든 칸을 캐시 미스로 본다")
    void readFailureIsMiss() {
        given(redisTemplate.opsForValue()).willReturn(valueOperations);
        given(valueOperations.multiGet(anyList())).willThrow(new RedisConnectionFailureException("Redis down"));

        assertThat(cache.readCells(CELLS)).isEmpty();
        assertThat(count("read", "error")).isEqualTo(2.0);
    }

    @Test
    @DisplayName("Redis 응답이 늦어 타임아웃이 나도 캐시 미스로 본다")
    void readTimeoutIsMiss() {
        given(redisTemplate.opsForValue()).willReturn(valueOperations);
        given(valueOperations.multiGet(anyList())).willThrow(new QueryTimeoutException("Redis timeout"));

        assertThat(cache.readCells(CELLS)).isEmpty();
    }

    @Test
    @DisplayName("깨진 값이 있는 칸만 캐시 미스로 보고 나머지는 읽는다")
    void brokenValueIsMiss() {
        given(redisTemplate.opsForValue()).willReturn(valueOperations);
        given(valueOperations.multiGet(anyList()))
                .willReturn(Arrays.asList("not-json", "{\"overflow\":false,\"pins\":[]}"));

        assertThat(cache.readCells(CELLS)).containsOnlyKeys(CELLS.get(1));
        assertThat(count("read", "error")).isEqualTo(1.0);
        assertThat(count("read", "hit")).isEqualTo(1.0);
    }

    @Test
    @DisplayName("Redis에 저장하지 못해도 예외를 던지지 않는다")
    void writeFailureIsIgnored() {
        given(redisTemplate.opsForValue()).willReturn(valueOperations);
        willThrow(new RedisConnectionFailureException("Redis down"))
                .given(valueOperations).set(anyString(), anyString(), any(Duration.class));

        assertThatCode(() -> cache.writeCell(CELLS.get(0), CellPins.of(List.of()))).doesNotThrowAnyException();
        assertThat(count("write", "error")).isEqualTo(1.0);
    }

    @Test
    @DisplayName("Redis에서 지우지 못해도 예외를 던지지 않는다")
    void evictFailureIsIgnored() {
        given(redisTemplate.delete(anyCollection())).willThrow(new RedisConnectionFailureException("Redis down"));

        assertThatCode(() -> cache.evictCells(CELLS)).doesNotThrowAnyException();
        assertThat(count("evict", "error")).isEqualTo(2.0);
    }

    private double count(String operation, String result) {
        return meterRegistry.counter(MapPinCellCache.METRIC, "operation", operation, "result", result).count();
    }
}
