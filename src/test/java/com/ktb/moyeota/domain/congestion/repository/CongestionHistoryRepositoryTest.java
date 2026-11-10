package com.ktb.moyeota.domain.congestion.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.ktb.moyeota.domain.congestion.document.CauseReport;
import com.ktb.moyeota.domain.congestion.document.CongestionHistory;
import com.ktb.moyeota.domain.congestion.model.CauseCode;
import com.ktb.moyeota.domain.congestion.model.CongestionLevel;
import com.ktb.moyeota.domain.congestion.status.CongestionStatus;
import com.ktb.moyeota.domain.congestion.support.CongestionMongoScripts;
import com.ktb.moyeota.global.config.MongoTestContainerConfig;
import java.time.Instant;
import java.util.List;
import org.bson.Document;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.mongodb.test.autoconfigure.DataMongoTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;

@DataMongoTest
@Import({MongoTestContainerConfig.class, CongestionHistoryRepository.class})
class CongestionHistoryRepositoryTest {

    private static final String COLLECTION = "congestion_history";
    private static final long ONE_WEEK_SECONDS = 604_800L;
    private static final Instant MORNING = Instant.parse("2026-11-03T01:00:00Z");

    @Autowired
    private CongestionHistoryRepository historyRepository;

    @Autowired
    private MongoTemplate mongoTemplate;

    @BeforeEach
    void setUp() throws Exception {
        mongoTemplate.dropCollection(COLLECTION);
        CongestionMongoScripts.applyIndexes();
    }

    @Test
    @DisplayName("이력은 calculated_at · spot_id 기준 시계열 컬렉션이고 일주일 뒤 지워진다")
    void createsTimeSeriesCollectionWithTtl() {
        Document options = collectionOptions();

        Document timeseries = options.get("timeseries", Document.class);
        assertThat(timeseries.getString("timeField")).isEqualTo("calculated_at");
        assertThat(timeseries.getString("metaField")).isEqualTo("spot_id");
        assertThat(timeseries.getString("granularity")).isEqualTo("minutes");
        assertThat(((Number) options.get("expireAfterSeconds")).longValue()).isEqualTo(ONE_WEEK_SECONDS);
    }

    @Test
    @DisplayName("스팟의 기간 안 이력을 구간 끝 시각 순으로 읽는다")
    void findsSpotHistoryInRangeOrderedByWindowEnd() {
        historyRepository.append(history(101L, MORNING.plusSeconds(1_800), CongestionStatus.CROWDED));
        historyRepository.append(history(101L, MORNING.plusSeconds(600), CongestionStatus.CONGESTED));
        historyRepository.append(history(101L, MORNING.plusSeconds(7_200), CongestionStatus.SMOOTH));
        historyRepository.append(history(201L, MORNING.plusSeconds(600), CongestionStatus.ACCIDENT));

        List<CongestionHistory> found = historyRepository.findBySpot(101L, MORNING, MORNING.plusSeconds(3_600));

        assertThat(found).extracting(CongestionHistory::getStatus)
                .containsExactly(CongestionStatus.CONGESTED, CongestionStatus.CROWDED);
        assertThat(found.getFirst()).satisfies(history -> {
            assertThat(history.getSpotId()).isEqualTo(101L);
            assertThat(history.getWindowMinutes()).isEqualTo(10);
            assertThat(history.getCongestionLevel()).isEqualTo(CongestionLevel.MEDIUM);
            assertThat(history.getCauses()).containsExactly(new CauseReport(CauseCode.RUSH_HOUR, 3));
            assertThat(history.getSummary()).isEqualTo("출근 인파로 혼잡");
            assertThat(history.getReportCount()).isEqualTo(3);
            assertThat(history.getEvidence()).isEmpty();
        });
    }

    @Test
    @DisplayName("같은 구간 이력이 두 번 들어가도 읽을 때는 하나만 돌려준다")
    void returnsDuplicatedWindowOnce() {
        Instant windowEnd = MORNING.plusSeconds(600);
        historyRepository.append(history(101L, windowEnd, CongestionStatus.CROWDED));
        historyRepository.append(history(101L, windowEnd, CongestionStatus.CROWDED));

        List<CongestionHistory> found = historyRepository.findBySpot(101L, MORNING, MORNING.plusSeconds(3_600));

        assertThat(mongoTemplate.count(new Query(), COLLECTION))
                .isEqualTo(2);
        assertThat(found).hasSize(1);
    }

    private Document collectionOptions() {
        Document collection = mongoTemplate.getDb()
                .listCollections()
                .filter(new Document("name", COLLECTION))
                .first();
        assertThat(collection).isNotNull();
        return collection.get("options", Document.class);
    }

    private static CongestionHistory history(Long spotId, Instant windowEnd, CongestionStatus status) {
        return CongestionHistory.of(spotId, windowEnd, 10, status, CongestionLevel.MEDIUM,
                List.of(new CauseReport(CauseCode.RUSH_HOUR, 3)), "출근 인파로 혼잡", 3);
    }
}
