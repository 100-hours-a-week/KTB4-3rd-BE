package com.ktb.moyeota.domain.taxipot.stream;

import static org.assertj.core.api.Assertions.assertThat;

import com.ktb.moyeota.domain.taxipot.matching.GeoPoint;
import com.ktb.moyeota.domain.taxipot.matching.MatchingRules;
import com.ktb.moyeota.domain.taxipot.message.MatchCanceledData;
import com.ktb.moyeota.domain.taxipot.message.MatchDecidedData;
import com.ktb.moyeota.domain.taxipot.message.MatchLeftData;
import com.ktb.moyeota.domain.taxipot.message.MatchPlaceData;
import com.ktb.moyeota.domain.taxipot.message.MatchRejectedData;
import com.ktb.moyeota.domain.taxipot.message.MatchRequestData;
import com.ktb.moyeota.domain.taxipot.message.MatchingMessages;
import com.ktb.moyeota.domain.taxipot.model.MatchApplyOutcome.RejectReason;
import com.ktb.moyeota.global.event.EventEnvelope;
import com.ktb.moyeota.global.event.EventEnvelopeFactory;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Properties;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.apache.kafka.streams.StreamsBuilder;
import org.apache.kafka.streams.StreamsConfig;
import org.apache.kafka.streams.TestInputTopic;
import org.apache.kafka.streams.TestOutputTopic;
import org.apache.kafka.streams.TopologyTestDriver;
import org.apache.kafka.streams.state.KeyValueIterator;
import org.apache.kafka.streams.test.TestRecord;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.jackson.autoconfigure.JacksonAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.support.serializer.JacksonJsonDeserializer;
import org.springframework.kafka.support.serializer.JacksonJsonSerializer;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

@SpringBootTest(classes = RegionMatchingProcessorTest.class)
@ImportAutoConfiguration(JacksonAutoConfiguration.class)
class RegionMatchingProcessorTest {

    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");
    private static final Instant START = LocalDateTime.of(2026, 10, 28, 9, 0).atZone(SEOUL).toInstant();
    private static final LocalDateTime DEPARTURE_AT = LocalDateTime.of(2026, 10, 28, 9, 30);
    private static final Duration SWEEP = Duration.ofSeconds(5);
    private static final String PANGYO = "2079:5606";
    private static final String NEXT_CELL = "2079:5607";
    private static final long ORIGIN_LAT = 37_394_776;
    private static final long ORIGIN_LNG = 127_111_158;
    private static final GeoPoint DEST = new GeoPoint(37_498_086, 127_028_001);

    @Autowired
    private JsonMapper jsonMapper;

    private final SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
    private TopologyTestDriver driver;
    private TestInputTopic<String, EventEnvelope<Object>> requests;
    private TestOutputTopic<String, EventEnvelope<MatchDecidedData>> decisions;

    @BeforeEach
    void setUp() {
        EventEnvelopeFactory envelopeFactory = new EventEnvelopeFactory(Clock.fixed(START, SEOUL));
        StreamsBuilder builder = new StreamsBuilder();
        MatchingTopology.define(builder, jsonMapper, () -> new RegionMatchingProcessor(
                new MatchingRules(100, 4), 8, SWEEP, SEOUL, envelopeFactory, jsonMapper, meterRegistry));
        Properties config = new Properties();
        config.put(StreamsConfig.APPLICATION_ID_CONFIG, "moyeota-matching");
        config.put(StreamsConfig.BOOTSTRAP_SERVERS_CONFIG, "dummy:9092");
        driver = new TopologyTestDriver(builder.build(), config, START);

        JacksonJsonSerializer<EventEnvelope<Object>> serializer = new JacksonJsonSerializer<>(jsonMapper);
        serializer.setAddTypeInfo(false);
        requests = driver.createInputTopic(MatchingMessages.REQUEST_TOPIC, new StringSerializer(), serializer);
        JacksonJsonDeserializer<EventEnvelope<MatchDecidedData>> deserializer = new JacksonJsonDeserializer<>(
                new TypeReference<EventEnvelope<MatchDecidedData>>() {
                }, jsonMapper);
        deserializer.setUseTypeHeaders(false);
        decisions = driver.createOutputTopic(MatchingMessages.DECISION_TOPIC, new StringDeserializer(), deserializer);
    }

    @AfterEach
    void tearDown() {
        driver.close();
    }

    @Test
    @DisplayName("둘만으로는 묶이지 않는 A·B 사이에 C가 오면 다음 주기에 셋이 한 팟이 된다")
    void opensPotForThree() {
        requested(PANGYO, request(101, 0), request(102, 1_600), request(104, 800));

        sweep();

        TestRecord<String, EventEnvelope<MatchDecidedData>> decision = decisions.readRecord();
        assertThat(decision.key()).isEqualTo(PANGYO);
        assertThat(decision.value().eventType()).isEqualTo(MatchingMessages.DECIDED);
        assertThat(decision.value().data().resultId()).isEqualTo("101,102,104");
        assertThat(decision.value().data().potKey()).isEqualTo("101,102,104");
        assertThat(decision.value().data().originAnchorRequestId()).isEqualTo(104L);
        assertThat(decision.value().data().departureAt()).isEqualTo(DEPARTURE_AT);
        assertThat(decisions.isEmpty()).isTrue();
    }

    @Test
    @DisplayName("요청 반영과 주기 실행을 각각 타이머로 잰다")
    void timesEventsAndSweeps() {
        requested(PANGYO, request(101, 0), request(102, 0));

        sweep();

        assertThat(meterRegistry.timer(MatchingMetrics.EVENT, MatchingMetrics.ENGINE_TAG, "streams").count())
                .isEqualTo(2);
        assertThat(meterRegistry.timer(MatchingMetrics.SWEEP, MatchingMetrics.ENGINE_TAG, "streams").count())
                .isEqualTo(1);
    }

    @Test
    @DisplayName("주기가 돌기 전에는 결정을 내지 않는다")
    void waitsForSweep() {
        requested(PANGYO, request(101, 0), request(102, 0));

        driver.advanceWallClockTime(SWEEP.minusSeconds(1));

        assertThat(decisions.isEmpty()).isTrue();
    }

    @Test
    @DisplayName("취소한 요청은 묶이지 않는다")
    void skipsCanceled() {
        requested(PANGYO, request(101, 0), request(102, 0), request(104, 0));
        send(PANGYO, MatchingMessages.CANCELED, new MatchCanceledData(102));

        sweep();

        assertThat(decisions.readValue().data().resultId()).isEqualTo("101,104");
    }

    @Test
    @DisplayName("좌표가 같아도 다른 칸의 요청과는 섞이지 않는다")
    void keepsRegionsApart() {
        requested(PANGYO, request(101, 0));
        requested(NEXT_CELL, request(102, 0));

        sweep();

        assertThat(decisions.isEmpty()).isTrue();
    }

    @Test
    @DisplayName("못 묶인 요청은 남아 있다가 다음 주기에 새 요청과 묶인다")
    void keepsWaitingForNextSweep() {
        requested(PANGYO, request(101, 0));
        sweep();
        assertThat(decisions.isEmpty()).isTrue();

        requested(PANGYO, request(102, 0));
        sweep();

        assertThat(decisions.readValue().data().resultId()).isEqualTo("101,102");
    }

    @Test
    @DisplayName("팟이 생긴 뒤 온 요청은 다음 주기에 그 팟에 합류한다")
    void joinsOpenPot() {
        requested(PANGYO, request(101, 0), request(102, 1_600), request(104, 800));
        sweep();
        decisions.readValue();

        requested(PANGYO, request(107, 800));
        sweep();

        MatchDecidedData join = decisions.readValue().data();
        assertThat(join.resultId()).isEqualTo("101,102,104:107");
        assertThat(join.potKey()).isEqualTo("101,102,104");
        assertThat(join.originAnchorRequestId()).isNull();
        assertThat(join.requests()).extracting(MatchRequestData::requestId).containsExactly(107L);
    }

    @Test
    @DisplayName("꽉 찬 팟에서 한 명이 나가면 남은 사람의 팟 키로 다음 요청이 합류한다")
    void leaveReturnsSeat() {
        requested(PANGYO, request(101, 0), request(102, 0), request(104, 0), request(105, 0));
        sweep();
        decisions.readValue();
        requested(PANGYO, request(107, 0));
        sweep();
        assertThat(decisions.isEmpty()).isTrue();

        send(PANGYO, MatchingMessages.LEFT, new MatchLeftData(102));
        sweep();

        assertThat(decisions.readValue().data().resultId()).isEqualTo("101,104,105:107");
    }

    @Test
    @DisplayName("새 팟 반영이 거절되면 취소하지 않은 요청은 대기로 돌아가 다시 묶인다")
    void rejectedPotReturnsRequests() {
        requested(PANGYO, request(101, 0), request(102, 0), request(104, 0));
        sweep();
        decisions.readValue();

        send(PANGYO, MatchingMessages.CANCELED, new MatchCanceledData(101));
        send(PANGYO, MatchingMessages.REJECTED, new MatchRejectedData(
                "101,102,104", "101,102,104", List.of(101L, 102L, 104L), RejectReason.REQUEST_NOT_MATCHING));
        sweep();

        assertThat(decisions.readValue().data().resultId()).isEqualTo("102,104");
    }

    @Test
    @DisplayName("출발 시각이 지난 요청과 팟은 상태에서 지운다")
    void dropsDeparted() {
        requested(PANGYO, request(101, 0, LocalDateTime.ofInstant(START, SEOUL)));

        driver.advanceWallClockTime(Duration.ofMinutes(1).plus(SWEEP));

        assertThat(decisions.isEmpty()).isTrue();
        assertThat(isEmpty(MatchingTopology.REQUEST_STORE)).isTrue();
        assertThat(isEmpty(MatchingTopology.POT_STORE)).isTrue();
    }

    private void sweep() {
        driver.advanceWallClockTime(SWEEP);
    }

    private boolean isEmpty(String storeName) {
        try (KeyValueIterator<String, Object> all = driver.<String, Object>getKeyValueStore(storeName).all()) {
            return !all.hasNext();
        }
    }

    private void requested(String region, MatchRequestData... requestData) {
        for (MatchRequestData data : requestData) {
            send(region, MatchingMessages.REQUESTED, data);
        }
    }

    private void send(String region, String eventType, Object data) {
        requests.pipeInput(region, new EventEnvelope<>(
                eventType + "-" + System.nanoTime(), eventType, MatchingMessages.SCHEMA_VERSION,
                START.atZone(SEOUL).toOffsetDateTime(), data));
    }

    private static MatchRequestData request(long requestId, long originLatOffset) {
        return request(requestId, originLatOffset, DEPARTURE_AT);
    }

    private static MatchRequestData request(long requestId, long originLatOffset, LocalDateTime departureAt) {
        return new MatchRequestData(requestId, requestId,
                place("출발지", ORIGIN_LAT + originLatOffset, ORIGIN_LNG),
                place("도착지", DEST.latMicros(), DEST.lngMicros()),
                departureAt, departureAt.minusMinutes(30));
    }

    private static MatchPlaceData place(String name, long latMicros, long lngMicros) {
        return new MatchPlaceData(name, BigDecimal.valueOf(latMicros, 6), BigDecimal.valueOf(lngMicros, 6));
    }
}
