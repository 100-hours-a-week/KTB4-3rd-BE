package com.ktb.moyeota.domain.congestion.cache;

import com.ktb.moyeota.global.common.Viewport;
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
import org.springframework.data.redis.core.RedisOperations;
import org.springframework.data.redis.core.SessionCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
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

    private static final double EARTH_RADIUS_METERS = 6_372_797.560856;
    private static final double EDGE_MARGIN_DEGREES = 0.0001;

    private final StringRedisTemplate redisTemplate;
    private final JsonMapper jsonMapper;

    public void put(CachedSpot spot) {
        putAll(List.of(spot));
    }

    public void putAll(List<CachedSpot> spots) {
        write(spots);
    }

    public void reload(List<CachedSpot> spots) {
        if (!write(spots)) {
            return;
        }
        try {
            redisTemplate.opsForValue().set(LOADED_KEY, "1");
        } catch (DataAccessException e) {
            log.warn("[CONGESTION_CACHE_WRITE_FAILED] Redis 에 스팟 위치를 다 채웠다는 표시를 남기지 못했습니다.", e);
        }
    }

    private boolean write(List<CachedSpot> spots) {
        Map<String, String> values = new LinkedHashMap<>();
        Map<String, Point> locations = new LinkedHashMap<>();
        for (CachedSpot spot : spots) {
            String member = String.valueOf(spot.id());
            try {
                values.put(member, jsonMapper.writeValueAsString(spot));
            } catch (JacksonException e) {
                log.error("[CONGESTION_CACHE_WRITE_FAILED] 스팟 최신값을 JSON 으로 바꾸지 못했습니다. spot_id={}",
                        spot.id(), e);
                continue;
            }
            if (spot.lat() != null && spot.lng() != null) {
                locations.put(member, new Point(spot.lng(), spot.lat()));
            }
        }
        if (values.isEmpty()) {
            return spots.isEmpty();
        }
        try {
            redisTemplate.opsForHash().putAll(KEY, values);
            if (!locations.isEmpty()) {
                redisTemplate.opsForGeo().add(GEO_KEY, locations);
            }
            return true;
        } catch (DataAccessException e) {
            log.warn("[CONGESTION_CACHE_WRITE_FAILED] Redis 에 스팟 최신값을 저장하지 못했습니다. spots={}",
                    values.size(), e);
            return false;
        }
    }

    public SpotsInBounds findInBounds(Viewport viewport) {
        List<String> ids;
        List<Object> read;
        try {
            ids = redisTemplate.opsForGeo().search(GEO_KEY, centerOf(viewport), boxOf(viewport)).getContent().stream()
                    .map(result -> result.getContent().getName())
                    .toList();
            read = redisTemplate.executePipelined(loadedAndValues(ids));
        } catch (DataAccessException e) {
            log.warn("[CONGESTION_CACHE_READ_FAILED] Redis 에서 화면 안 스팟을 읽지 못했습니다.", e);
            return new SpotsInBounds.Unavailable();
        }
        if (!Boolean.TRUE.equals(read.get(0))) {
            return new SpotsInBounds.NotIndexed();
        }
        if (ids.isEmpty()) {
            return new SpotsInBounds.Found(List.of());
        }
        @SuppressWarnings("unchecked")
        List<String> values = (List<String>) read.get(1);
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

    private static SessionCallback<Object> loadedAndValues(List<String> ids) {
        return new SessionCallback<>() {
            @Override
            @SuppressWarnings("unchecked")
            public <K, V> Object execute(RedisOperations<K, V> operations) {
                RedisOperations<String, String> redis = (RedisOperations<String, String>) operations;
                redis.hasKey(LOADED_KEY);
                if (!ids.isEmpty()) {
                    redis.<String, String>opsForHash().multiGet(KEY, ids);
                }
                return null;
            }
        };
    }

    public Optional<List<CachedSpot>> findAll() {
        Map<Object, Object> entries;
        try {
            entries = redisTemplate.opsForHash().entries(KEY);
        } catch (DataAccessException e) {
            log.warn("[CONGESTION_CACHE_READ_FAILED] Redis 에서 스팟 최신값을 읽지 못했습니다.", e);
            return Optional.empty();
        }
        List<CachedSpot> spots = new ArrayList<>(entries.size());
        for (Map.Entry<Object, Object> entry : entries.entrySet()) {
            try {
                spots.add(jsonMapper.readValue((String) entry.getValue(), CachedSpot.class));
            } catch (JacksonException | IllegalArgumentException | ClassCastException e) {
                log.warn("[CONGESTION_CACHE_READ_FAILED] 캐시 값을 읽지 못해 건너뜁니다. spot_id={}",
                        entry.getKey(), e);
            }
        }
        return Optional.of(spots);
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
