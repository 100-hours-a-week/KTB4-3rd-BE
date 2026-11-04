package com.ktb.moyeota.domain.congestion.cache;

import static org.assertj.core.api.Assertions.assertThat;

import com.ktb.moyeota.domain.congestion.document.CauseReport;
import com.ktb.moyeota.domain.congestion.document.SpotType;
import com.ktb.moyeota.domain.congestion.model.CauseCode;
import com.ktb.moyeota.domain.congestion.model.CongestionLevel;
import com.ktb.moyeota.domain.congestion.status.CongestionStatus;
import com.ktb.moyeota.global.config.RedisTestContainerConfig;
import java.time.Instant;
import java.util.List;
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

    @Autowired
    private CongestionLatestCache latestCache;

    @Autowired
    private StringRedisTemplate redisTemplate;

    @BeforeEach
    void setUp() {
        redisTemplate.delete(CongestionLatestCache.KEY);
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

    private static CachedSpot spot(Long id, String name, CongestionStatus status) {
        return new CachedSpot(id, SpotType.STATION, name, 37.3948, 127.1112, status, CongestionLevel.HIGH,
                List.of(new CauseReport(CauseCode.RUSH_HOUR, 3)), "출근 인파로 붐빔", 3, WINDOW_END,
                WINDOW_END.plusSeconds(600));
    }
}
