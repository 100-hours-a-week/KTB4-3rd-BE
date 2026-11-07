package com.ktb.moyeota.domain.congestion.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.ktb.moyeota.domain.congestion.config.CongestionSpotProperties;
import com.ktb.moyeota.domain.congestion.document.Signal;
import com.ktb.moyeota.domain.congestion.document.Spot;
import com.ktb.moyeota.domain.congestion.message.ChangedComment;
import com.ktb.moyeota.domain.congestion.message.ChangedPost;
import com.ktb.moyeota.domain.congestion.message.PostChangedData;
import com.ktb.moyeota.domain.congestion.repository.SignalRepository;
import com.ktb.moyeota.domain.congestion.repository.SpotRepository;
import com.ktb.moyeota.domain.congestion.support.CongestionMongoScripts;
import com.ktb.moyeota.global.config.MongoTestContainerConfig;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import org.assertj.core.groups.Tuple;
import org.bson.Document;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.mongodb.test.autoconfigure.DataMongoTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.data.mongodb.core.MongoTemplate;

@DataMongoTest
@Import({MongoTestContainerConfig.class, CongestionSignalService.class, SpotRepository.class,
        SignalRepository.class, CongestionSignalServiceTest.PropertiesConfig.class})
class CongestionSignalServiceTest {

    private static final BigDecimal PANGYO_LAT = new BigDecimal("37.394800");
    private static final BigDecimal PANGYO_LNG = new BigDecimal("127.111200");
    private static final BigDecimal NEAR_PANGYO_LAT = new BigDecimal("37.396600");
    private static final BigDecimal FAR_LAT = new BigDecimal("37.556300");
    private static final BigDecimal FAR_LNG = new BigDecimal("126.923600");
    private static final LocalDateTime POSTED_AT = LocalDateTime.of(2026, 11, 2, 10, 7);
    private static final Instant POSTED_INSTANT = Instant.parse("2026-11-02T01:07:00Z");

    @Autowired
    private CongestionSignalService signalService;

    @Autowired
    private MongoTemplate mongoTemplate;

    @BeforeEach
    void setUp() throws Exception {
        mongoTemplate.dropCollection("spots");
        mongoTemplate.dropCollection("signals");
        CongestionMongoScripts.applyIndexes();
        mongoTemplate.getCollection("spots").insertOne(new Document("_id", 101L)
                .append("type", "STATION")
                .append("name", "판교역")
                .append("location", new Document("type", "Point")
                        .append("coordinates", List.of(PANGYO_LNG.doubleValue(), PANGYO_LAT.doubleValue()))));
    }

    @Test
    @DisplayName("원글은 반경 안 가장 가까운 스팟에 매어 사본을 넣고 스팟의 마지막 글 시각을 올린다")
    void loadsPostIntoNearestSpot() {
        SignalLoadOutcome outcome = signalService.load(postMessage(NEAR_PANGYO_LAT, PANGYO_LNG));

        assertThat(outcome).isEqualTo(SignalLoadOutcome.LOADED);
        assertThat(mongoTemplate.findAll(Signal.class)).singleElement().satisfies(signal -> {
            assertThat(signal.getPostId()).isEqualTo(1L);
            assertThat(signal.getCommentId()).isNull();
            assertThat(signal.getSpotId()).isEqualTo(101L);
            assertThat(signal.getAuthorKey()).isEqualTo("10");
            assertThat(signal.getCreatedAt()).isEqualTo(POSTED_INSTANT);
            assertThat(signal.getTitle()).isEqualTo("판교역 혼잡");
        });
        assertThat(lastSignalAt()).isEqualTo(POSTED_INSTANT);
    }

    @Test
    @DisplayName("반경 안에 스팟이 없으면 아무것도 넣지 않는다")
    void skipsPostOutsideRadius() {
        SignalLoadOutcome outcome = signalService.load(postMessage(FAR_LAT, FAR_LNG));

        assertThat(outcome).isEqualTo(SignalLoadOutcome.OUT_OF_RANGE);
        assertThat(mongoTemplate.findAll(Signal.class)).isEmpty();
        assertThat(lastSignalAt()).isNull();
    }

    @Test
    @DisplayName("같은 메시지가 두 번 와도 사본은 하나만 남는다")
    void ignoresDuplicateMessage() {
        signalService.load(postMessage(PANGYO_LAT, PANGYO_LNG));

        SignalLoadOutcome outcome = signalService.load(postMessage(PANGYO_LAT, PANGYO_LNG));

        assertThat(outcome).isEqualTo(SignalLoadOutcome.DUPLICATE);
        assertThat(mongoTemplate.findAll(Signal.class)).hasSize(1);
    }

    @Test
    @DisplayName("댓글은 원글 좌표의 스팟에 매고, 원글 사본이 없으면 문맥용으로 함께 넣는다")
    void loadsCommentWithPostContext() {
        LocalDateTime commentedAt = POSTED_AT.plusMinutes(2);
        PostChangedData message = new PostChangedData(post(PANGYO_LAT, PANGYO_LNG),
                new ChangedComment(7L, 11L, "저도 기다리는 중", commentedAt));

        SignalLoadOutcome outcome = signalService.load(message);

        assertThat(outcome).isEqualTo(SignalLoadOutcome.LOADED);
        assertThat(mongoTemplate.findAll(Signal.class))
                .extracting(Signal::getPostId, Signal::getCommentId, Signal::getSpotId)
                .containsExactlyInAnyOrder(Tuple.tuple(1L, null, 101L), Tuple.tuple(1L, 7L, 101L));
        assertThat(lastSignalAt()).isEqualTo(POSTED_INSTANT.plusSeconds(120));
    }

    @Test
    @DisplayName("원글이 먼저 들어와 있으면 댓글만 더 넣는다")
    void loadsCommentAfterPost() {
        signalService.load(postMessage(PANGYO_LAT, PANGYO_LNG));

        signalService.load(new PostChangedData(post(PANGYO_LAT, PANGYO_LNG),
                new ChangedComment(7L, 11L, "저도 기다리는 중", POSTED_AT.plusMinutes(2))));

        assertThat(mongoTemplate.findAll(Signal.class)).hasSize(2);
    }

    private static PostChangedData postMessage(BigDecimal lat, BigDecimal lng) {
        return new PostChangedData(post(lat, lng), null);
    }

    private static ChangedPost post(BigDecimal lat, BigDecimal lng) {
        return new ChangedPost(1L, 10L, "판교역 혼잡", "출구 사람 많아요", lat, lng, POSTED_AT);
    }

    private Instant lastSignalAt() {
        return mongoTemplate.findById(101L, Spot.class).getLastSignalAt();
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class PropertiesConfig {

        @Bean
        CongestionSpotProperties congestionSpotProperties() {
            return new CongestionSpotProperties(500);
        }
    }
}
