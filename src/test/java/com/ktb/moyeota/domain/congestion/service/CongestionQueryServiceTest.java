package com.ktb.moyeota.domain.congestion.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ktb.moyeota.domain.congestion.cache.CachedSpot;
import com.ktb.moyeota.domain.congestion.cache.CongestionLatestCache;
import com.ktb.moyeota.domain.congestion.config.CongestionWindowProperties;
import com.ktb.moyeota.domain.congestion.document.CauseReport;
import com.ktb.moyeota.domain.congestion.document.SpotType;
import com.ktb.moyeota.domain.congestion.error.CongestionErrorCode;
import com.ktb.moyeota.domain.congestion.model.CauseCode;
import com.ktb.moyeota.domain.congestion.model.CongestionLevel;
import com.ktb.moyeota.domain.congestion.repository.SpotRepository;
import com.ktb.moyeota.domain.congestion.status.CongestionStatus;
import com.ktb.moyeota.domain.congestion.window.CongestionWindowCalculator;
import com.ktb.moyeota.global.config.MongoTestContainerConfig;
import com.ktb.moyeota.global.config.RedisTestContainerConfig;
import com.ktb.moyeota.global.exception.BusinessException;
import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Date;
import java.util.List;
import org.bson.Document;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.data.mongodb.test.autoconfigure.DataMongoTest;
import org.springframework.boot.data.redis.autoconfigure.DataRedisAutoConfiguration;
import org.springframework.boot.jackson.autoconfigure.JacksonAutoConfiguration;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;

@DataMongoTest
@ImportAutoConfiguration({DataRedisAutoConfiguration.class, JacksonAutoConfiguration.class})
@Import({MongoTestContainerConfig.class, RedisTestContainerConfig.class, CongestionLatestCache.class,
        SpotRepository.class, CongestionWindowCalculator.class, CongestionQueryServiceTest.PropertiesConfig.class})
class CongestionQueryServiceTest {

    private static final Long PANGYO = 101L;
    private static final List<CauseReport> RUSH_HOUR = List.of(new CauseReport(CauseCode.RUSH_HOUR, 3));

    @Autowired
    private CongestionLatestCache latestCache;

    @Autowired
    private SpotRepository spotRepository;

    @Autowired
    private CongestionWindowCalculator calculator;

    @Autowired
    private MongoTemplate mongoTemplate;

    @Autowired
    private StringRedisTemplate redisTemplate;

    @BeforeEach
    void setUp() {
        mongoTemplate.dropCollection("spots");
        redisTemplate.delete("congestion:spots:latest");
    }

    @Test
    @DisplayName("캐시에 있는 스팟의 화면 상태 · 원인 문장 · 결과 구간 끝을 돌려준다")
    void findsSpotFromCache() {
        latestCache.put(result(CongestionStatus.CONGESTED, RUSH_HOUR, at("07:10"), at("07:10")));

        CongestionSpotDetail detail = findSpotAt("07:15");

        assertThat(detail).isEqualTo(new CongestionSpotDetail(PANGYO, SpotType.STATION, "판교역", 37.3948, 127.1112,
                CongestionStatus.CONGESTED, at("07:10"), "출근 인파로 혼잡", false));
    }

    @Test
    @DisplayName("캐시에 없으면 MongoDB 스팟을 읽는다")
    void findsSpotFromMongoWhenCacheMissing() {
        mongoTemplate.getCollection("spots").insertOne(new Document("_id", PANGYO)
                .append("type", "STATION")
                .append("name", "판교역")
                .append("location", new Document("type", "Point").append("coordinates", List.of(127.1112, 37.3948)))
                .append("status", "CROWDED")
                .append("causes", List.of(new Document("cause_code", "EVENT").append("report_count", 4)))
                .append("summary", "행사 때문에 사람이 매우 많아요.")
                .append("calculated_at", Date.from(at("07:10")))
                .append("result_window_end", Date.from(at("07:10")))
                .append("valid_until", Date.from(at("07:30"))));

        CongestionSpotDetail detail = findSpotAt("07:15");

        assertThat(detail.status()).isEqualTo(CongestionStatus.CROWDED);
        assertThat(detail.reason()).isEqualTo("행사 때문에 사람이 매우 많아요.");
        assertThat(detail.lat()).isEqualTo(37.3948);
    }

    @Test
    @DisplayName("없는 스팟이면 SPOT_NOT_FOUND")
    void throwsWhenSpotMissing() {
        assertThatThrownBy(() -> findSpotAt("07:15"))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(CongestionErrorCode.SPOT_NOT_FOUND));
    }

    @Test
    @DisplayName("표시 기한이 지났으면 화면 상태 · 원인 문장 · 결과 구간 끝을 비운다")
    void hidesExpiredResult() {
        latestCache.put(result(CongestionStatus.CONGESTED, RUSH_HOUR, at("07:10"), at("07:10")));

        CongestionSpotDetail detail = findSpotAt("07:30");

        assertThat(detail.status()).isNull();
        assertThat(detail.reason()).isNull();
        assertThat(detail.windowEnd()).isNull();
        assertThat(detail.name()).isEqualTo("판교역");
    }

    @Test
    @DisplayName("원인이 없거나 원활이면 원인 문장을 비운다")
    void hidesReasonWithoutCauseOrWhenSmooth() {
        latestCache.put(result(CongestionStatus.CONGESTED, List.of(), at("07:10"), at("07:10")));
        assertThat(findSpotAt("07:15")).satisfies(detail -> {
            assertThat(detail.status()).isEqualTo(CongestionStatus.CONGESTED);
            assertThat(detail.reason()).isNull();
        });

        latestCache.put(result(CongestionStatus.SMOOTH, RUSH_HOUR, at("07:10"), at("07:10")));
        assertThat(findSpotAt("07:15").reason()).isNull();
    }

    @Test
    @DisplayName("분석이 실패한 뒤에도 몇 시 기준인지는 마지막 결과의 구간 끝이다")
    void usesResultWindowEndAfterFailure() {
        latestCache.put(new CachedSpot(PANGYO, SpotType.STATION, "판교역", 37.3948, 127.1112,
                CongestionStatus.CONGESTED, CongestionLevel.MEDIUM, RUSH_HOUR, "출근 인파로 혼잡", 3,
                at("07:20"), at("07:30"), at("07:20"), at("07:10")));

        assertThat(findSpotAt("07:22").windowEnd()).isEqualTo(at("07:10"));
    }

    @Test
    @DisplayName("요청을 보낸 구간의 결과를 기다리는 동안 분석 중이고, 다음 구간이 끝나면 풀린다")
    void analyzingUntilNextWindowEnds() {
        latestCache.put(result(CongestionStatus.CONGESTED, RUSH_HOUR, at("07:10"), at("07:20")));

        CongestionSpotDetail waiting = findSpotAt("07:21");
        CongestionSpotDetail expired = findSpotAt("07:30");

        assertThat(waiting.analyzing()).isTrue();
        assertThat(waiting.status()).isEqualTo(CongestionStatus.CONGESTED);
        assertThat(waiting.windowEnd()).isEqualTo(at("07:10"));
        assertThat(expired.analyzing()).isFalse();
    }

    @Test
    @DisplayName("결과가 오면 분석 중이 풀린다")
    void notAnalyzingAfterResult() {
        latestCache.put(result(CongestionStatus.CONGESTED, RUSH_HOUR, at("07:20"), at("07:20")));

        assertThat(findSpotAt("07:21").analyzing()).isFalse();
    }

    private CongestionSpotDetail findSpotAt(String time) {
        Clock clock = Clock.fixed(at(time), ZoneOffset.UTC);
        return new CongestionQueryService(latestCache, spotRepository, calculator, clock).findSpot(PANGYO);
    }

    private CachedSpot result(CongestionStatus status, List<CauseReport> causes, Instant windowEnd,
            Instant pendingWindowEnd) {
        Instant validUntil = calculator.windowContaining(calculator.windowContaining(windowEnd).end().toInstant())
                .end().toInstant();
        return new CachedSpot(PANGYO, SpotType.STATION, "판교역", 37.3948, 127.1112, status, CongestionLevel.MEDIUM,
                causes, "출근 인파로 혼잡", 3, windowEnd, validUntil, pendingWindowEnd, windowEnd);
    }

    private static Instant at(String time) {
        return OffsetDateTime.parse("2026-11-02T" + time + ":00+09:00").toInstant();
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class PropertiesConfig {

        @Bean
        CongestionWindowProperties congestionWindowProperties() {
            return new CongestionWindowProperties(7, 10, 17, 20, 10, 60);
        }
    }
}
