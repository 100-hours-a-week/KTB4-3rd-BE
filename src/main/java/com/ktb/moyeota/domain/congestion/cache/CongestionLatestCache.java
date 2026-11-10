package com.ktb.moyeota.domain.congestion.cache;

import java.util.ArrayList;
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
public class CongestionLatestCache {

    static final String KEY = "congestion:spots:latest";

    private final StringRedisTemplate redisTemplate;
    private final JsonMapper jsonMapper;

    public void put(CachedSpot spot) {
        putAll(List.of(spot));
    }

    public void putAll(List<CachedSpot> spots) {
        if (spots.isEmpty()) {
            return;
        }
        Map<String, String> values = new LinkedHashMap<>();
        for (CachedSpot spot : spots) {
            try {
                values.put(String.valueOf(spot.id()), jsonMapper.writeValueAsString(spot));
            } catch (JacksonException e) {
                log.error("[CONGESTION_CACHE_WRITE_FAILED] 스팟 최신값을 JSON 으로 바꾸지 못했습니다. spot_id={}",
                        spot.id(), e);
            }
        }
        if (values.isEmpty()) {
            return;
        }
        try {
            redisTemplate.opsForHash().putAll(KEY, values);
        } catch (DataAccessException e) {
            log.warn("[CONGESTION_CACHE_WRITE_FAILED] Redis 에 스팟 최신값을 저장하지 못했습니다. spots={}",
                    values.size(), e);
        }
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
