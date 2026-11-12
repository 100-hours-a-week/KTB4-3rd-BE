package com.ktb.moyeota.domain.congestion.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.ktb.moyeota.domain.congestion.cache.CachedSpot;
import com.ktb.moyeota.domain.congestion.cache.CongestionLatestCache;
import com.ktb.moyeota.domain.congestion.config.CongestionStatusProperties;
import com.ktb.moyeota.domain.congestion.config.CongestionWindowProperties;
import com.ktb.moyeota.domain.congestion.document.CauseReport;
import com.ktb.moyeota.domain.congestion.document.CongestionHistory;
import com.ktb.moyeota.domain.congestion.document.Spot;
import com.ktb.moyeota.domain.congestion.message.AnalysisStatus;
import com.ktb.moyeota.domain.congestion.message.CongestionAnalysis;
import com.ktb.moyeota.domain.congestion.message.CongestionAnalysisResultData;
import com.ktb.moyeota.domain.congestion.message.CongestionCause;
import com.ktb.moyeota.domain.congestion.model.CauseCode;
import com.ktb.moyeota.domain.congestion.model.CongestionLevel;
import com.ktb.moyeota.domain.congestion.repository.CongestionHistoryRepository;
import com.ktb.moyeota.domain.congestion.repository.SpotRepository;
import com.ktb.moyeota.domain.congestion.status.CongestionStatus;
import com.ktb.moyeota.domain.congestion.status.CongestionStatusMapper;
import com.ktb.moyeota.domain.congestion.support.CongestionMongoScripts;
import com.ktb.moyeota.domain.congestion.window.CongestionWindowCalculator;
import com.ktb.moyeota.global.config.MongoTestContainerConfig;
import com.ktb.moyeota.global.config.RedisTestContainerConfig;
import java.time.Instant;
import java.time.OffsetDateTime;
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
        CongestionResultService.class, SpotRepository.class,
        CongestionHistoryRepository.class, CongestionStatusMapper.class, CongestionWindowCalculator.class,
        CongestionResultServiceTest.PropertiesConfig.class})
class CongestionResultServiceTest {

    private static final Long PANGYO = 101L;

    @Autowired
    private CongestionResultService resultService;

    @Autowired
    private MongoTemplate mongoTemplate;

    @Autowired
    private CongestionLatestCache latestCache;

    @Autowired
    private StringRedisTemplate redisTemplate;

    @BeforeEach
    void setUp() throws Exception {
        mongoTemplate.dropCollection("spots");
        mongoTemplate.dropCollection("congestion_history");
        CongestionMongoScripts.applyIndexes();
        clearCache();
        mongoTemplate.getCollection("spots").insertOne(new Document("_id", PANGYO)
                .append("type", "STATION")
                .append("name", "판교역"));
    }

    @Test
    @DisplayName("첫 결과는 최신값을 적고 화면 상태 이력을 남긴다")
    void appliesFirstResult() {
        ResultApplyOutcome outcome = resultService.apply(success("07:10", CongestionLevel.MEDIUM, "출근 인파로 혼잡"));

        assertThat(outcome).isEqualTo(ResultApplyOutcome.APPLIED);
        assertThat(spot()).satisfies(spot -> {
            assertThat(spot.getStatus()).isEqualTo(CongestionStatus.CONGESTED);
            assertThat(spot.getCongestionLevel()).isEqualTo(CongestionLevel.MEDIUM);
            assertThat(spot.getCauses()).containsExactly(new CauseReport(CauseCode.RUSH_HOUR, 3));
            assertThat(spot.getSummary()).isEqualTo("출근 인파로 혼잡");
            assertThat(spot.getReportCount()).isEqualTo(3);
            assertThat(spot.getCalculatedAt()).isEqualTo(at("07:10"));
            assertThat(spot.getValidUntil()).isEqualTo(at("07:30"));
        });
        assertThat(histories()).singleElement().satisfies(history -> {
            assertThat(history.getCalculatedAt()).isEqualTo(at("07:10"));
            assertThat(history.getWindowMinutes()).isEqualTo(10);
            assertThat(history.getStatus()).isEqualTo(CongestionStatus.CONGESTED);
        });
    }

    @Test
    @DisplayName("화면 상태가 그대로면 이력 없이 최신값만 바꾼다")
    void updatesLatestWithoutHistoryWhenStatusSame() {
        resultService.apply(success("07:10", CongestionLevel.MEDIUM, "출근 인파로 혼잡"));

        ResultApplyOutcome outcome = resultService.apply(success("07:20", CongestionLevel.MEDIUM, "여전히 혼잡"));

        assertThat(outcome).isEqualTo(ResultApplyOutcome.APPLIED);
        assertThat(spot().getSummary()).isEqualTo("여전히 혼잡");
        assertThat(spot().getCalculatedAt()).isEqualTo(at("07:20"));
        assertThat(spot().getValidUntil()).isEqualTo(at("07:40"));
        assertThat(histories()).hasSize(1);
    }

    @Test
    @DisplayName("이미 처리한 구간이나 더 오래된 구간의 결과는 버린다")
    void dropsStaleResult() {
        resultService.apply(success("07:20", CongestionLevel.MEDIUM, "출근 인파로 혼잡"));

        ResultApplyOutcome older = resultService.apply(success("07:10", CongestionLevel.HIGH, "늦게 온 결과"));
        ResultApplyOutcome same = resultService.apply(success("07:20", CongestionLevel.HIGH, "다시 온 결과"));

        assertThat(older).isEqualTo(ResultApplyOutcome.STALE);
        assertThat(same).isEqualTo(ResultApplyOutcome.ALREADY_APPLIED);
        assertThat(spot().getStatus()).isEqualTo(CongestionStatus.CONGESTED);
        assertThat(spot().getCalculatedAt()).isEqualTo(at("07:20"));
        assertThat(histories()).hasSize(1);
    }

    @Test
    @DisplayName("다음 구간을 분석하는 동안에도 직전 결과가 보이도록 표시 기한을 결과 구간 끝에서 두 구간 뒤로 잡는다")
    void keepsLatestVisibleWhileNextWindowIsAnalyzed() {
        resultService.apply(success("09:50", CongestionLevel.MEDIUM, "출근 인파로 혼잡"));

        assertThat(spot().getValidUntil()).isEqualTo(at("11:00"));
    }

    @Test
    @DisplayName("분석이 실패하면 직전 값은 두고 처리한 구간 끝만 올린다")
    void keepsLatestWhenAnalysisFailed() {
        resultService.apply(success("07:10", CongestionLevel.MEDIUM, "출근 인파로 혼잡"));

        ResultApplyOutcome outcome = resultService.apply(new CongestionAnalysisResultData(
                PANGYO, time("07:20"), AnalysisStatus.FAILED, null, time("07:21")));

        assertThat(outcome).isEqualTo(ResultApplyOutcome.ANALYSIS_FAILED);
        assertThat(spot()).satisfies(spot -> {
            assertThat(spot.getStatus()).isEqualTo(CongestionStatus.CONGESTED);
            assertThat(spot.getSummary()).isEqualTo("출근 인파로 혼잡");
            assertThat(spot.getValidUntil()).isEqualTo(at("07:30"));
            assertThat(spot.getCalculatedAt()).isEqualTo(at("07:20"));
        });
        assertThat(histories()).hasSize(1);
    }

    @Test
    @DisplayName("결과 구간 끝은 결과를 반영할 때만 바뀌고 분석이 실패해도 마지막 결과의 구간 끝으로 남는다")
    void keepsResultWindowEndWhenAnalysisFailed() {
        resultService.apply(success("07:10", CongestionLevel.MEDIUM, "출근 인파로 혼잡"));
        assertThat(spot().getResultWindowEnd()).isEqualTo(at("07:10"));

        resultService.apply(new CongestionAnalysisResultData(
                PANGYO, time("07:20"), AnalysisStatus.FAILED, null, time("07:21")));

        assertThat(spot().getResultWindowEnd()).isEqualTo(at("07:10"));
        assertThat(cachedSpots()).singleElement()
                .satisfies(cached -> assertThat(cached.resultWindowEnd()).isEqualTo(at("07:10")));
    }

    @Test
    @DisplayName("혼잡 단계를 알 수 없으면 화면 상태를 비우고 바뀐 이력으로 남긴다")
    void clearsStatusWhenLevelUnknown() {
        resultService.apply(success("07:10", CongestionLevel.MEDIUM, "출근 인파로 혼잡"));

        resultService.apply(success("07:20", CongestionLevel.UNKNOWN, "판단할 글이 부족함"));

        assertThat(spot().getStatus()).isNull();
        assertThat(spot().getCongestionLevel()).isEqualTo(CongestionLevel.UNKNOWN);
        assertThat(histories()).extracting(CongestionHistory::getStatus)
                .containsExactlyInAnyOrder(CongestionStatus.CONGESTED, null);
    }

    @Test
    @DisplayName("없는 스팟의 결과는 아무것도 쓰지 않는다")
    void ignoresUnknownSpot() {
        ResultApplyOutcome outcome = resultService.apply(new CongestionAnalysisResultData(
                999L, time("07:10"), AnalysisStatus.SUCCESS, analysis(CongestionLevel.MEDIUM, "없는 스팟"),
                time("07:11")));

        assertThat(outcome).isEqualTo(ResultApplyOutcome.UNKNOWN_SPOT);
        assertThat(histories()).isEmpty();
        assertThat(mongoTemplate.findById(999L, Spot.class)).isNull();
    }

    @Test
    @DisplayName("최신값을 바꾸면 바뀐 스팟을 Redis 캐시에도 넣는다")
    void refreshesCacheWhenApplied() {
        resultService.apply(success("07:10", CongestionLevel.MEDIUM, "출근 인파로 혼잡"));

        assertThat(cachedSpots()).singleElement().satisfies(cached -> {
            assertThat(cached.id()).isEqualTo(PANGYO);
            assertThat(cached.status()).isEqualTo(CongestionStatus.CONGESTED);
            assertThat(cached.validUntil()).isEqualTo(at("07:30"));
        });
    }

    @Test
    @DisplayName("버린 결과는 Redis 캐시를 건드리지 않는다")
    void skipsCacheWhenStale() {
        resultService.apply(success("07:20", CongestionLevel.MEDIUM, "출근 인파로 혼잡"));
        clearCache();

        resultService.apply(success("07:10", CongestionLevel.HIGH, "늦게 온 결과"));

        assertThat(cachedSpots()).isEmpty();
    }

    @Test
    @DisplayName("이미 처리한 구간의 결과가 다시 오면 캐시만 다시 쓴다")
    void rewritesCacheWhenAlreadyApplied() {
        resultService.apply(success("07:10", CongestionLevel.MEDIUM, "출근 인파로 혼잡"));
        clearCache();

        ResultApplyOutcome outcome = resultService.apply(success("07:10", CongestionLevel.HIGH, "다시 온 결과"));

        assertThat(outcome).isEqualTo(ResultApplyOutcome.ALREADY_APPLIED);
        assertThat(cachedSpots()).singleElement().satisfies(cached -> {
            assertThat(cached.status()).isEqualTo(CongestionStatus.CONGESTED);
            assertThat(cached.calculatedAt()).isEqualTo(at("07:10"));
        });
        assertThat(histories()).hasSize(1);
    }

    private static CongestionAnalysisResultData success(String windowEnd, CongestionLevel level, String summary) {
        return new CongestionAnalysisResultData(PANGYO, time(windowEnd), AnalysisStatus.SUCCESS,
                analysis(level, summary), time(windowEnd).plusMinutes(1));
    }

    private static CongestionAnalysis analysis(CongestionLevel level, String summary) {
        return new CongestionAnalysis(level, 3, List.of(new CongestionCause(CauseCode.RUSH_HOUR, 3)), summary);
    }

    private List<CachedSpot> cachedSpots() {
        return latestCache.findAll().orElseThrow();
    }

    private void clearCache() {
        redisTemplate.delete("congestion:spots:latest");
    }

    private Spot spot() {
        return mongoTemplate.findById(PANGYO, Spot.class);
    }

    private List<CongestionHistory> histories() {
        return mongoTemplate.findAll(CongestionHistory.class);
    }

    private static OffsetDateTime time(String time) {
        return OffsetDateTime.parse("2026-11-02T" + time + ":00+09:00");
    }

    private static Instant at(String time) {
        return time(time).toInstant();
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class PropertiesConfig {

        @Bean
        CongestionStatusProperties congestionStatusProperties() {
            return new CongestionStatusProperties(3, 3);
        }

        @Bean
        CongestionWindowProperties congestionWindowProperties() {
            return new CongestionWindowProperties(7, 10, 17, 20, 10, 60);
        }
    }
}
