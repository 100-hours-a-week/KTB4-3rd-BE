package com.ktb.moyeota.domain.congestion.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.ktb.moyeota.domain.congestion.document.Spot;
import com.ktb.moyeota.domain.congestion.document.SpotType;
import com.ktb.moyeota.domain.congestion.support.CongestionMongoScripts;
import com.ktb.moyeota.global.config.MongoTestContainerConfig;
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
import org.testcontainers.mongodb.MongoDBContainer;

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
    private MongoDBContainer mongoContainer;

    @Autowired
    private SpotRepository spotRepository;

    @BeforeEach
    void setUp() throws Exception {
        mongoTemplate.dropCollection("spots");
        CongestionMongoScripts.applyIndexes(mongoContainer);
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
