package com.ktb.moyeota.domain.congestion.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.ktb.moyeota.domain.congestion.document.CongestionLatest;
import com.ktb.moyeota.domain.congestion.document.Spot;
import com.ktb.moyeota.domain.congestion.document.SpotType;
import com.ktb.moyeota.domain.congestion.model.CongestionLevel;
import com.ktb.moyeota.domain.congestion.status.CongestionStatus;
import com.ktb.moyeota.domain.congestion.support.CongestionMongoScripts;
import com.ktb.moyeota.global.common.Viewport;
import com.ktb.moyeota.global.config.MongoTestContainerConfig;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import org.bson.Document;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.mongodb.test.autoconfigure.DataMongoTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.mongodb.core.MongoTemplate;

@DataMongoTest
@Import({MongoTestContainerConfig.class, SpotRepository.class})
class SpotRepositoryTest {

    private static final double LAT = 37.3948;
    private static final double LNG = 127.1112;
    private static final double LAT_PER_100M = 0.0009;
    private static final int RADIUS_METERS = 500;

    @Autowired
    private MongoTemplate mongoTemplate;

    @Autowired
    private SpotRepository spotRepository;

    @BeforeEach
    void setUp() throws Exception {
        mongoTemplate.dropCollection("spots");
        CongestionMongoScripts.applyIndexes();
    }

    @Test
    @DisplayName("반경 안 스팟 중 가장 가까운 스팟을 찾는다")
    void findsNearestSpotInRadius() {
        insertSpots(List.of(
                spot(101L, "판교역", LAT + LAT_PER_100M * 3, LNG),
                spot(102L, "판교테크노밸리", LAT + LAT_PER_100M, LNG)));

        Optional<Spot> found = spotRepository.findNearest(LAT, LNG, RADIUS_METERS);

        assertThat(found).get().satisfies(spot -> {
            assertThat(spot.getId()).isEqualTo(102L);
            assertThat(spot.getName()).isEqualTo("판교테크노밸리");
            assertThat(spot.getType()).isEqualTo(SpotType.STATION);
            assertThat(spot.getLocation().getX()).isEqualTo(LNG);
            assertThat(spot.getLastSignalAt()).isNull();
        });
    }

    @Test
    @DisplayName("반경 밖에만 스팟이 있으면 찾지 않는다")
    void returnsEmptyWhenSpotIsOutsideRadius() {
        insertSpots(List.of(spot(101L, "판교역", LAT + LAT_PER_100M * 6, LNG)));

        assertThat(spotRepository.findNearest(LAT, LNG, RADIUS_METERS)).isEmpty();
    }

    @Test
    @DisplayName("스팟이 하나도 없으면 찾지 않는다")
    void returnsEmptyWhenNoSpot() {
        assertThat(spotRepository.findNearest(LAT, LNG, RADIUS_METERS)).isEmpty();
    }

    @Test
    @DisplayName("마지막 글 시각이 기준 시각 이후인 스팟 번호만 고른다")
    void findsIdsSignaledSince() {
        Instant since = Instant.parse("2026-11-01T22:00:00Z");
        insertSpots(List.of(
                spot(101L, "판교역", LAT, LNG).append("last_signal_at", Date.from(since)),
                spot(102L, "정자역", LAT, LNG).append("last_signal_at", Date.from(since.plusSeconds(610))),
                spot(103L, "미금역", LAT, LNG).append("last_signal_at", Date.from(since.minusSeconds(1))),
                spot(104L, "서현역", LAT, LNG)));

        List<Long> ids = spotRepository.findIdsSignaledSince(since);

        assertThat(ids).containsExactly(101L, 102L);
    }

    @Test
    @DisplayName("마지막 글 시각은 더 늦은 시각일 때만 올린다")
    void raisesLastSignalAtOnlyForward() {
        Instant first = Instant.parse("2026-11-02T01:07:00Z");
        insertSpots(List.of(spot(101L, "판교역", LAT, LNG)));

        spotRepository.raiseLastSignalAt(101L, first);
        spotRepository.raiseLastSignalAt(101L, first.minusSeconds(60));

        assertThat(lastSignalAt(101L)).isEqualTo(first);

        spotRepository.raiseLastSignalAt(101L, first.plusSeconds(60));

        assertThat(lastSignalAt(101L)).isEqualTo(first.plusSeconds(60));
    }

    private Instant lastSignalAt(Long spotId) {
        return mongoTemplate.findById(spotId, Spot.class).getLastSignalAt();
    }

    @Test
    @DisplayName("결과가 한 번이라도 반영된 스팟만 최신값 목록으로 읽는다")
    void findsSpotsWithLatest() {
        insertSpots(List.of(
                spot(101L, "판교역", LAT, LNG)
                        .append("calculated_at", Date.from(Instant.parse("2026-11-03T22:10:00Z"))),
                spot(102L, "정자역", LAT, LNG)));

        assertThat(spotRepository.findAllLatest()).extracting(Spot::getId).containsExactly(101L);
    }

    @Test
    @DisplayName("화면 범위 안에서 결과가 있는 스팟만 읽고, 넓은 화면의 남쪽 경계 바로 안쪽 스팟도 빠뜨리지 않는다")
    void findsLatestSpotsInBounds() {
        Date calculatedAt = Date.from(Instant.parse("2026-11-03T22:10:00Z"));
        insertSpots(List.of(
                spot(101L, "남쪽 경계 가운데 1m 안", 37.00001, 127.5).append("calculated_at", calculatedAt),
                spot(102L, "남서 모서리", 37.0, 127.0).append("calculated_at", calculatedAt),
                spot(103L, "북동 모서리", 38.0, 128.0).append("calculated_at", calculatedAt),
                spot(104L, "결과 없음", 37.5, 127.5),
                spot(105L, "동쪽 경계 밖", 37.5, 128.01).append("calculated_at", calculatedAt)));

        List<Spot> spots = spotRepository.findLatestInBounds(new Viewport(
                new BigDecimal("37.0"), new BigDecimal("127.0"), new BigDecimal("38.0"), new BigDecimal("128.0")));

        assertThat(spots).extracting(Spot::getId).containsExactlyInAnyOrder(101L, 102L, 103L);
    }

    @Test
    @DisplayName("분석 요청 · 결과 반영 · 실패 구간 넘기기마다 버전을 하나씩 올리고, 글 시각과 지난 결과는 올리지 않는다")
    void raisesVersionOnlyWhenCachedValueChanges() {
        Instant windowEnd = Instant.parse("2026-11-03T22:10:00Z");
        insertSpots(List.of(spot(101L, "판교역", LAT, LNG)));

        spotRepository.markAnalyzing(101L, windowEnd);
        assertThat(version(101L)).isEqualTo(1L);

        spotRepository.applyLatest(101L, latest(windowEnd));
        assertThat(version(101L)).isEqualTo(2L);

        spotRepository.advanceCalculatedAt(101L, windowEnd.plusSeconds(600));
        assertThat(version(101L)).isEqualTo(3L);

        spotRepository.raiseLastSignalAt(101L, windowEnd.plusSeconds(700));
        spotRepository.applyLatest(101L, latest(windowEnd));
        spotRepository.advanceCalculatedAt(101L, windowEnd);
        assertThat(version(101L)).isEqualTo(3L);
    }

    private Long version(Long spotId) {
        return mongoTemplate.findById(spotId, Spot.class).getVersion();
    }

    private static CongestionLatest latest(Instant calculatedAt) {
        return new CongestionLatest(CongestionStatus.CROWDED, CongestionLevel.HIGH, List.of(), "요약", 3,
                calculatedAt, calculatedAt.plusSeconds(1200));
    }

    private void insertSpots(List<Document> spots) {
        mongoTemplate.getCollection("spots").insertMany(spots);
    }

    private static Document spot(Long id, String name, double lat, double lng) {
        return new Document("_id", id)
                .append("type", "STATION")
                .append("name", name)
                .append("location", new Document("type", "Point").append("coordinates", List.of(lng, lat)));
    }
}
