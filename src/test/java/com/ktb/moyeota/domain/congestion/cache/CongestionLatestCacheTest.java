package com.ktb.moyeota.domain.congestion.cache;

import static org.assertj.core.api.Assertions.assertThat;

import com.ktb.moyeota.domain.congestion.document.CauseReport;
import com.ktb.moyeota.domain.congestion.document.SpotType;
import com.ktb.moyeota.domain.congestion.model.CauseCode;
import com.ktb.moyeota.domain.congestion.model.CongestionLevel;
import com.ktb.moyeota.domain.congestion.status.CongestionStatus;
import com.ktb.moyeota.global.common.Viewport;
import com.ktb.moyeota.global.config.RedisTestContainerConfig;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.stream.IntStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.data.redis.test.autoconfigure.DataRedisTest;
import org.springframework.boot.jackson.autoconfigure.JacksonAutoConfiguration;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.core.StringRedisTemplate;

@DataRedisTest
@ImportAutoConfiguration(JacksonAutoConfiguration.class)
@Import({RedisTestContainerConfig.class, CongestionLatestCache.class})
class CongestionLatestCacheTest {

    private static final Instant WINDOW_END = Instant.parse("2026-11-02T22:10:00Z");
    private static final Viewport PANGYO_VIEW = new Viewport(
            new BigDecimal("37.38"), new BigDecimal("127.10"), new BigDecimal("37.41"), new BigDecimal("127.13"));

    @Autowired
    private CongestionLatestCache latestCache;

    @Autowired
    private StringRedisTemplate redisTemplate;

    @BeforeEach
    void setUp() {
        redisTemplate.delete(List.of(CongestionLatestCache.KEY, CongestionLatestCache.GEO_KEY,
                CongestionLatestCache.LOADED_KEY));
    }

    @Test
    @DisplayName("스팟 최신값을 넣고 그대로 읽는다")
    void putsAndReadsSpot() {
        CachedSpot pangyo = spot(101L, "판교역", CongestionStatus.CROWDED);

        latestCache.put(pangyo);

        assertThat(latestCache.findAll()).hasValueSatisfying(spots -> assertThat(spots).containsExactly(pangyo));
    }

    @Test
    @DisplayName("같은 스팟을 다시 넣으면 최신값으로 덮어쓴다")
    void overwritesSameSpot() {
        latestCache.put(spot(101L, "판교역", CongestionStatus.CONGESTED));

        latestCache.put(spot(101L, "판교역", CongestionStatus.ACCIDENT));

        assertThat(latestCache.findAll()).hasValueSatisfying(spots -> assertThat(spots)
                .singleElement().extracting(CachedSpot::status).isEqualTo(CongestionStatus.ACCIDENT));
    }

    @Test
    @DisplayName("여러 스팟을 한 번에 넣고, 비어 있으면 빈 목록을 돌려준다")
    void putsAllAndReadsEmpty() {
        assertThat(latestCache.findAll()).hasValueSatisfying(spots -> assertThat(spots).isEmpty());

        latestCache.putAll(List.of(spot(101L, "판교역", CongestionStatus.CROWDED),
                spot(201L, "강남역", CongestionStatus.SMOOTH)));

        assertThat(latestCache.findAll()).hasValueSatisfying(spots -> assertThat(spots)
                .extracting(CachedSpot::id).containsExactlyInAnyOrder(101L, 201L));
    }

    @Test
    @DisplayName("스팟 하나를 읽고, 없으면 비어 있다")
    void getsOneSpot() {
        CachedSpot pangyo = spot(101L, "판교역", CongestionStatus.CROWDED);
        latestCache.putAll(List.of(pangyo, spot(201L, "강남역", CongestionStatus.SMOOTH)));

        assertThat(latestCache.get(101L)).contains(pangyo);
        assertThat(latestCache.get(999L)).isEmpty();
    }

    @Test
    @DisplayName("다 채운 적이 없으면 결과만 들어와 있어도 위치 키를 믿지 않는다")
    void notIndexedBeforeReload() {
        latestCache.put(spot(101L, "판교역", CongestionStatus.CROWDED));

        assertThat(latestCache.findInBounds(PANGYO_VIEW)).isInstanceOf(SpotsInBounds.NotIndexed.class);
    }

    @Test
    @DisplayName("다 채운 뒤에는 화면 안 스팟만 읽는다")
    void findsOnlySpotsInBoundsAfterReload() {
        CachedSpot pangyo = spot(101L, "판교역", CongestionStatus.CROWDED);
        latestCache.reload(List.of(pangyo, spot(201L, "강남역", 37.4979, 127.0276)));

        assertThat(latestCache.findInBounds(PANGYO_VIEW)).isEqualTo(new SpotsInBounds.Found(List.of(pangyo)));
    }

    @Test
    @DisplayName("넓은 화면에서도 모서리와 남쪽 경계 바로 안쪽 스팟을 찾는다")
    void findsSpotsOnEdgesOfWideViewport() {
        latestCache.reload(List.of(spot(101L, "남서 모서리", 37.0, 127.0), spot(102L, "북동 모서리", 38.0, 128.0),
                spot(103L, "남쪽 경계 가운데 1m 안", 37.00001, 127.5), spot(104L, "남동 모서리", 37.0, 128.0)));

        SpotsInBounds found = latestCache.findInBounds(new Viewport(
                new BigDecimal("37.0"), new BigDecimal("127.0"), new BigDecimal("38.0"), new BigDecimal("128.0")));

        assertThat(found).isInstanceOfSatisfying(SpotsInBounds.Found.class, result -> assertThat(result.spots())
                .extracting(CachedSpot::id).containsExactlyInAnyOrder(101L, 102L, 103L, 104L));
    }

    @Test
    @DisplayName("화면 안 스팟이 천 개를 넘어도 모두 읽는다")
    void findsMoreThanThousandSpotsInBounds() {
        List<CachedSpot> spots = IntStream.range(0, 2_500)
                .mapToObj(i -> spot(1_000L + i, "정류장" + i, 37.381 + (i / 50) * 0.0005, 127.101 + (i % 50) * 0.0005))
                .toList();
        latestCache.reload(spots);

        SpotsInBounds found = latestCache.findInBounds(PANGYO_VIEW);

        assertThat(found).isInstanceOfSatisfying(SpotsInBounds.Found.class, result -> assertThat(result.spots())
                .containsExactlyInAnyOrderElementsOf(spots));
    }

    @Test
    @DisplayName("다 채운 뒤 결과 저장으로 처음 들어온 스팟도 위치로 찾는다")
    void findsSpotAddedByResultAfterReload() {
        latestCache.reload(List.of());

        CachedSpot pangyo = spot(101L, "판교역", CongestionStatus.CROWDED);
        latestCache.put(pangyo);

        assertThat(latestCache.findInBounds(PANGYO_VIEW)).isEqualTo(new SpotsInBounds.Found(List.of(pangyo)));
    }

    @Test
    @DisplayName("화면 안에 스팟이 없으면 빈 목록이다")
    void findsNothingInEmptyViewport() {
        latestCache.reload(List.of(spot(201L, "강남역", 37.4979, 127.0276)));

        assertThat(latestCache.findInBounds(PANGYO_VIEW)).isEqualTo(new SpotsInBounds.Found(List.of()));
    }

    @Test
    @DisplayName("위치는 있는데 결과가 사라졌으면 다시 채우도록 알린다")
    void notIndexedWhenResultMissing() {
        latestCache.reload(List.of(spot(101L, "판교역", CongestionStatus.CROWDED)));
        redisTemplate.delete(CongestionLatestCache.KEY);

        assertThat(latestCache.findInBounds(PANGYO_VIEW)).isInstanceOf(SpotsInBounds.NotIndexed.class);
    }

    private static CachedSpot spot(Long id, String name, double lat, double lng) {
        return new CachedSpot(id, SpotType.STATION, name, lat, lng, CongestionStatus.CROWDED, CongestionLevel.HIGH,
                List.of(), "요약", 3, WINDOW_END, WINDOW_END.plusSeconds(600), WINDOW_END, WINDOW_END);
    }

    private static CachedSpot spot(Long id, String name, CongestionStatus status) {
        return new CachedSpot(id, SpotType.STATION, name, 37.3948, 127.1112, status, CongestionLevel.HIGH,
                List.of(new CauseReport(CauseCode.RUSH_HOUR, 3)), "출근 인파로 붐빔", 3, WINDOW_END,
                WINDOW_END.plusSeconds(600), WINDOW_END, WINDOW_END);
    }
}
