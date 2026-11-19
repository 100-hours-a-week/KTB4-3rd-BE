package com.ktb.moyeota.domain.congestion.cache;

import com.ktb.moyeota.global.common.Viewport;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.data.geo.Point;
import org.springframework.data.redis.connection.RedisGeoCommands.DistanceUnit;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.data.redis.domain.geo.BoundingBox;
import org.springframework.data.redis.domain.geo.GeoReference;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

@Slf4j
@Component
@RequiredArgsConstructor
public class CongestionLatestCache {

    static final String KEY = "congestion:spots:latest";
    static final String GEO_KEY = "congestion:spots:geo";
    static final String LOADED_KEY = "congestion:spots:loaded";
    static final String RELOADING_KEY = "congestion:spots:reloading";

    private static final Duration RELOAD_LOCK_TTL = Duration.ofSeconds(30);
    private static final int RELOAD_CHUNK_SIZE = 500;
    private static final List<Duration> RELOAD_RETRY_BACKOFFS = List.of(
            Duration.ofMillis(100), Duration.ofMillis(200), Duration.ofMillis(400));

    private static final double EARTH_RADIUS_METERS = 6_372_797.560856;
    private static final double EDGE_MARGIN_DEGREES = 0.0001;

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static final RedisScript<List<String>> LOADED_AND_VALUES = (RedisScript) RedisScript.of("""
            local result = {tostring(redis.call('EXISTS', KEYS[2]))}
            if result[1] == '0' then
                return result
            end
            for i = 1, #ARGV, 1000 do
                local values = redis.call('HMGET', KEYS[1], unpack(ARGV, i, math.min(i + 999, #ARGV)))
                for j = 1, #values do
                    result[#result + 1] = values[j]
                end
            end
            return result
            """, List.class);

    private static final RedisScript<Long> PUT_IF_NOT_OLDER = RedisScript.of("""
            for i = 1, #ARGV, 3 do
                local current = redis.call('HGET', KEYS[1], ARGV[i])
                local currentVersion = 0
                if current then
                    local ok, decoded = pcall(cjson.decode, current)
                    if ok and type(decoded) == 'table' then
                        currentVersion = tonumber(decoded.version) or 0
                    end
                end
                if currentVersion <= tonumber(ARGV[i + 2]) then
                    redis.call('HSET', KEYS[1], ARGV[i], ARGV[i + 1])
                end
            end
            return 0
            """, Long.class);

    private final StringRedisTemplate redisTemplate;
    private final JsonMapper jsonMapper;

    public void put(CachedSpot spot) {
        putAll(List.of(spot));
    }

    public void putAll(List<CachedSpot> spots) {
        write(spots);
    }

    public boolean tryStartReload() {
        try {
            return Boolean.TRUE.equals(redisTemplate.opsForValue().setIfAbsent(RELOADING_KEY, "1", RELOAD_LOCK_TTL));
        } catch (DataAccessException e) {
            log.warn("[CONGESTION_CACHE_RELOAD_LOCK_FAILED] Redis 에 다시 채우기 잠금을 걸지 못했습니다.", e);
            return false;
        }
    }

    public boolean reload(List<CachedSpot> spots) {
        for (int from = 0; from < spots.size(); from += RELOAD_CHUNK_SIZE) {
            if (!writeWithRetry(spots.subList(from, Math.min(from + RELOAD_CHUNK_SIZE, spots.size())))) {
                return false;
            }
        }
        try {
            redisTemplate.opsForValue().set(LOADED_KEY, "1");
            return true;
        } catch (DataAccessException e) {
            log.warn("[CONGESTION_CACHE_WRITE_FAILED] Redis 에 스팟 위치를 다 채웠다는 표시를 남기지 못했습니다.", e);
            return false;
        }
    }

    private boolean writeWithRetry(List<CachedSpot> chunk) {
        if (write(chunk)) {
            return true;
        }
        for (Duration backoff : RELOAD_RETRY_BACKOFFS) {
            try {
                Thread.sleep(backoff);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return false;
            }
            if (write(chunk)) {
                return true;
            }
        }
        return false;
    }

    private boolean write(List<CachedSpot> spots) {
        List<String> args = new ArrayList<>();
        Map<String, Point> locations = new LinkedHashMap<>();
        for (CachedSpot spot : spots) {
            String member = String.valueOf(spot.id());
            try {
                args.addAll(List.of(member, jsonMapper.writeValueAsString(spot),
                        String.valueOf(spot.version() == null ? 0 : spot.version())));
            } catch (JacksonException e) {
                log.error("[CONGESTION_CACHE_WRITE_FAILED] 스팟 최신값을 JSON 으로 바꾸지 못했습니다. spot_id={}",
                        spot.id(), e);
                continue;
            }
            if (spot.lat() != null && spot.lng() != null) {
                locations.put(member, new Point(spot.lng(), spot.lat()));
            }
        }
        if (args.isEmpty()) {
            return spots.isEmpty();
        }
        try {
            redisTemplate.execute(PUT_IF_NOT_OLDER, List.of(KEY), args.toArray());
            if (!locations.isEmpty()) {
                redisTemplate.opsForGeo().add(GEO_KEY, locations);
            }
            return true;
        } catch (DataAccessException e) {
            log.warn("[CONGESTION_CACHE_WRITE_FAILED] Redis 에 스팟 최신값을 저장하지 못했습니다. spots={}",
                    spots.size(), e);
            return false;
        }
    }

    public SpotsInBounds findInBounds(Viewport viewport) {
        List<String> ids;
        List<String> read;
        try {
            ids = redisTemplate.opsForGeo().search(GEO_KEY, centerOf(viewport), boxOf(viewport)).getContent().stream()
                    .map(result -> result.getContent().getName())
                    .toList();
            read = redisTemplate.execute(LOADED_AND_VALUES, List.of(KEY, LOADED_KEY), ids.toArray());
        } catch (DataAccessException e) {
            log.warn("[CONGESTION_CACHE_READ_FAILED] Redis 에서 화면 안 스팟을 읽지 못했습니다.", e);
            return new SpotsInBounds.Unavailable();
        }
        if (!"1".equals(read.getFirst())) {
            return new SpotsInBounds.NotIndexed();
        }
        List<String> values = read.subList(1, read.size());
        if (values.contains(null)) {
            return new SpotsInBounds.NotIndexed();
        }
        List<CachedSpot> spots = new ArrayList<>(values.size());
        for (int i = 0; i < values.size(); i++) {
            try {
                spots.add(jsonMapper.readValue(values.get(i), CachedSpot.class));
            } catch (JacksonException e) {
                log.warn("[CONGESTION_CACHE_READ_FAILED] 캐시 값을 읽지 못해 건너뜁니다. spot_id={}", ids.get(i), e);
            }
        }
        return new SpotsInBounds.Found(spots);
    }

    private static GeoReference<String> centerOf(Viewport viewport) {
        double lat = (viewport.swLat().doubleValue() + viewport.neLat().doubleValue()) / 2;
        double lng = (viewport.swLng().doubleValue() + viewport.neLng().doubleValue()) / 2;
        return GeoReference.fromCoordinate(lng, lat);
    }

    private static BoundingBox boxOf(Viewport viewport) {
        double south = viewport.swLat().doubleValue() - EDGE_MARGIN_DEGREES;
        double north = viewport.neLat().doubleValue() + EDGE_MARGIN_DEGREES;
        double lngSpan = viewport.neLng().doubleValue() - viewport.swLng().doubleValue() + 2 * EDGE_MARGIN_DEGREES;
        double widestLat = south <= 0 && 0 <= north ? 0 : Math.min(Math.abs(south), Math.abs(north));
        double width = Math.toRadians(lngSpan) * EARTH_RADIUS_METERS * Math.cos(Math.toRadians(widestLat));
        double height = Math.toRadians(north - south) * EARTH_RADIUS_METERS;
        return new BoundingBox(width, height, DistanceUnit.METERS);
    }

    public Optional<CachedSpot> get(Long spotId) {
        String value;
        try {
            value = redisTemplate.<String, String>opsForHash().get(KEY, String.valueOf(spotId));
        } catch (DataAccessException e) {
            log.warn("[CONGESTION_CACHE_READ_FAILED] Redis 에서 스팟 최신값을 읽지 못했습니다. spot_id={}", spotId, e);
            return Optional.empty();
        }
        if (value == null) {
            return Optional.empty();
        }
        try {
            return Optional.of(jsonMapper.readValue(value, CachedSpot.class));
        } catch (JacksonException e) {
            log.warn("[CONGESTION_CACHE_READ_FAILED] 캐시 값을 읽지 못했습니다. spot_id={}", spotId, e);
            return Optional.empty();
        }
    }
}
