package com.ktb.moyeota.domain.congestion.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.ktb.moyeota.domain.congestion.cache.CachedSpot;
import com.ktb.moyeota.domain.congestion.cache.CongestionLatestCache;
import com.ktb.moyeota.domain.congestion.cache.SpotsInBounds;
import com.ktb.moyeota.domain.congestion.repository.SpotRepository;
import com.ktb.moyeota.global.common.Viewport;
import com.ktb.moyeota.global.config.MongoTestContainerConfig;
import com.ktb.moyeota.global.config.RedisTestContainerConfig;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.bson.Document;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.data.mongodb.test.autoconfigure.DataMongoTest;
import org.springframework.boot.data.redis.autoconfigure.DataRedisAutoConfiguration;
import org.springframework.boot.jackson.autoconfigure.JacksonAutoConfiguration;
import org.springframework.context.annotation.Import;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;

@DataMongoTest
@ImportAutoConfiguration({DataRedisAutoConfiguration.class, JacksonAutoConfiguration.class})
@Import({MongoTestContainerConfig.class, RedisTestContainerConfig.class, CongestionLatestCache.class,
        SpotRepository.class})
class CongestionCacheReloaderTest {

    private static final String RELOADING_KEY = "congestion:spots:reloading";
    private static final Viewport PANGYO_VIEW = new Viewport(
            new BigDecimal("37.38"), new BigDecimal("127.10"), new BigDecimal("37.41"), new BigDecimal("127.13"));

    @Autowired
    private CongestionLatestCache latestCache;

    @Autowired
    private SpotRepository spotRepository;

    @Autowired
    private MongoTemplate mongoTemplate;

    @Autowired
    private StringRedisTemplate redisTemplate;

    private final List<Runnable> started = new CopyOnWriteArrayList<>();

    private CongestionCacheReloader reloader;

    @BeforeEach
    void setUp() {
        mongoTemplate.dropCollection("spots");
        redisTemplate.delete(List.of("congestion:spots:latest", "congestion:spots:geo", "congestion:spots:loaded",
                RELOADING_KEY));
        started.clear();
        reloader = new CongestionCacheReloader(latestCache, spotRepository, started::add);
    }

    @Test
    @DisplayName("다시 채우기 요청이 동시에 몰려도 잠금 하나당 한 번만 시작한다")
    void startsOnceForConcurrentRequests() throws Exception {
        int requests = 20;
        ExecutorService pool = Executors.newFixedThreadPool(requests);
        CountDownLatch ready = new CountDownLatch(1);
        try {
            for (int i = 0; i < requests; i++) {
                pool.submit(() -> {
                    ready.await();
                    reloader.requestReload();
                    return null;
                });
            }
            ready.countDown();
        } finally {
            pool.shutdown();
            assertThat(pool.awaitTermination(10, TimeUnit.SECONDS)).isTrue();
        }

        assertThat(started).hasSize(1);
    }

    @Test
    @DisplayName("다른 곳이 다시 채우는 중이면 시작하지 않고, 잠금이 풀리면 다시 시작한다")
    void startsAgainAfterLockReleased() {
        redisTemplate.opsForValue().set(RELOADING_KEY, "1");

        reloader.requestReload();

        assertThat(started).isEmpty();

        redisTemplate.delete(RELOADING_KEY);
        reloader.requestReload();

        assertThat(started).hasSize(1);
    }

    @Test
    @DisplayName("시작한 다시 채우기는 결과 있는 스팟을 채우고 다 채움 표시를 남긴다")
    void reloadFillsCacheAndMarksLoaded() {
        insertSpot(101L, "판교역", 37.3948, 127.1112, true);
        insertSpot(102L, "정자역", 37.3670, 127.1083, true);
        insertSpot(103L, "결과 없음", 37.3950, 127.1110, false);

        reloader.requestReload();
        started.getFirst().run();

        assertThat(latestCache.findInBounds(PANGYO_VIEW)).isInstanceOfSatisfying(SpotsInBounds.Found.class,
                found -> assertThat(found.spots()).extracting(CachedSpot::id).containsExactly(101L));
        assertThat(redisTemplate.hasKey("congestion:spots:loaded")).isTrue();
    }

    private void insertSpot(Long id, String name, double lat, double lng, boolean withResult) {
        Document spot = new Document("_id", id)
                .append("type", "STATION")
                .append("name", name)
                .append("location", new Document("type", "Point").append("coordinates", List.of(lng, lat)));
        if (withResult) {
            Date calculatedAt = Date.from(Instant.parse("2026-11-03T22:10:00Z"));
            spot.append("status", "CROWDED")
                    .append("causes", List.of())
                    .append("calculated_at", calculatedAt)
                    .append("result_window_end", calculatedAt)
                    .append("valid_until", Date.from(Instant.parse("2026-11-03T22:30:00Z")))
                    .append("version", 1L);
        }
        mongoTemplate.getCollection("spots").insertOne(spot);
    }
}
