package com.ktb.moyeota.domain.congestion.batch;

import static org.assertj.core.api.Assertions.assertThat;

import com.ktb.moyeota.domain.congestion.config.CongestionBatchProperties;
import com.ktb.moyeota.domain.congestion.config.CongestionWindowProperties;
import com.ktb.moyeota.domain.congestion.repository.CongestionJobRepository;
import com.ktb.moyeota.domain.congestion.repository.SpotRepository;
import com.ktb.moyeota.domain.congestion.window.CongestionWindow;
import com.ktb.moyeota.domain.congestion.window.CongestionWindowCalculator;
import com.ktb.moyeota.global.config.MongoTestContainerConfig;
import java.time.Clock;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import org.bson.Document;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.mongodb.test.autoconfigure.DataMongoTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.mongodb.core.MongoTemplate;

@DataMongoTest
@Import({MongoTestContainerConfig.class, CongestionJobRepository.class, SpotRepository.class})
class CongestionWindowCloserTest {

    private final CongestionWindowCalculator calculator =
            new CongestionWindowCalculator(new CongestionWindowProperties(7, 10, 17, 20, 10, 60));
    private final RecordingRequester requester = new RecordingRequester();

    @Autowired
    private MongoTemplate mongoTemplate;

    @Autowired
    private CongestionJobRepository jobRepository;

    @Autowired
    private SpotRepository spotRepository;

    @BeforeEach
    void setUp() {
        mongoTemplate.dropCollection("congestion_jobs");
        mongoTemplate.dropCollection("spots");
    }

    @Test
    @DisplayName("처음 돌면 막 끝난 구간 하나를 보내고 기록한다")
    void firstRunSendsLatestWindow() {
        insertSpot(101L, "2026-11-02T07:05:00+09:00");

        closerAt("2026-11-02T07:10:30+09:00").closeEndedWindows();

        assertThat(requester.requests).containsExactly(
                new Request(window("07:00", "07:10"), List.of(101L)));
        assertThat(lastWindowEnd()).isEqualTo(at("07:10"));
    }

    @Test
    @DisplayName("구간 끝에서 유예 30초가 지나기 전에는 그 구간을 보내지 않는다")
    void waitsForGrace() {
        jobRepository.start(at("07:00").toInstant());
        insertSpot(101L, "2026-11-02T07:05:00+09:00");

        closerAt("2026-11-02T07:10:20+09:00").closeEndedWindows();

        assertThat(requester.requests).isEmpty();
        assertThat(lastWindowEnd()).isEqualTo(at("07:00"));
    }

    @Test
    @DisplayName("놓친 구간을 60분 구간부터 10분 구간까지 차례로 보낸다")
    void catchesUpMissedWindows() {
        jobRepository.start(at("06:00").toInstant());
        insertSpot(101L, "2026-11-02T07:15:00+09:00");

        closerAt("2026-11-02T07:20:30+09:00").closeEndedWindows();

        assertThat(requester.requests).containsExactly(
                new Request(window("06:00", "07:00"), List.of(101L)),
                new Request(window("07:00", "07:10"), List.of(101L)),
                new Request(window("07:10", "07:20"), List.of(101L)));
        assertThat(lastWindowEnd()).isEqualTo(at("07:20"));
    }

    @Test
    @DisplayName("같은 시각에 두 대가 돌아도 구간은 한 번만 보낸다")
    void sendsOnceAcrossInstances() {
        jobRepository.start(at("07:00").toInstant());
        insertSpot(101L, "2026-11-02T07:05:00+09:00");

        closerAt("2026-11-02T07:10:30+09:00").closeEndedWindows();
        closerAt("2026-11-02T07:10:30+09:00").closeEndedWindows();

        assertThat(requester.requests).hasSize(1);
    }

    @Test
    @DisplayName("구간 시작 뒤에 글이 있는 스팟만 고르고, 유예 중 다음 구간 글이 온 스팟도 고른다")
    void picksSpotsSignaledSinceWindowStart() {
        jobRepository.start(at("07:00").toInstant());
        insertSpot(101L, "2026-11-02T07:05:00+09:00");
        insertSpot(102L, "2026-11-02T07:10:10+09:00");
        insertSpot(103L, "2026-11-02T06:59:59+09:00");
        insertSpot(104L, null);

        closerAt("2026-11-02T07:10:30+09:00").closeEndedWindows();

        assertThat(requester.requests).containsExactly(
                new Request(window("07:00", "07:10"), List.of(101L, 102L)));
    }

    @Test
    @DisplayName("글이 있는 스팟이 없으면 요청 없이 구간 끝만 기록한다")
    void recordsWindowWithoutSpots() {
        jobRepository.start(at("07:00").toInstant());

        closerAt("2026-11-02T07:10:30+09:00").closeEndedWindows();

        assertThat(requester.requests).isEmpty();
        assertThat(lastWindowEnd()).isEqualTo(at("07:10"));
    }

    private CongestionWindowCloser closerAt(String now) {
        Clock clock = Clock.fixed(OffsetDateTime.parse(now).toInstant(), ZoneOffset.UTC);
        return new CongestionWindowCloser(calculator, jobRepository, spotRepository, requester,
                new CongestionBatchProperties(Duration.ofSeconds(30)), clock);
    }

    private OffsetDateTime lastWindowEnd() {
        return jobRepository.findLastWindowEnd().orElseThrow().atOffset(ZoneOffset.ofHours(9));
    }

    private void insertSpot(Long id, String lastSignalAt) {
        Document spot = new Document("_id", id).append("type", "STATION").append("name", "스팟" + id);
        if (lastSignalAt != null) {
            spot.append("last_signal_at", Date.from(OffsetDateTime.parse(lastSignalAt).toInstant()));
        }
        mongoTemplate.getCollection("spots").insertOne(spot);
    }

    private static CongestionWindow window(String start, String end) {
        return new CongestionWindow(at(start), at(end));
    }

    private static OffsetDateTime at(String time) {
        return OffsetDateTime.parse("2026-11-02T" + time + ":00+09:00");
    }

    private record Request(CongestionWindow window, List<Long> spotIds) {
    }

    private static class RecordingRequester implements CongestionAnalysisRequester {

        private final List<Request> requests = new ArrayList<>();

        @Override
        public void request(CongestionWindow window, List<Long> spotIds) {
            requests.add(new Request(window, spotIds));
        }
    }
}
