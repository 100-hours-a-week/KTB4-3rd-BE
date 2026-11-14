package com.ktb.moyeota.domain.taxipot.stream;

import static org.assertj.core.api.Assertions.assertThat;

import com.ktb.moyeota.domain.taxipot.matching.MatchingRules;
import com.ktb.moyeota.domain.taxipot.message.MatchDecidedData;
import com.ktb.moyeota.domain.taxipot.message.MatchPlaceData;
import com.ktb.moyeota.domain.taxipot.message.MatchRequestData;
import com.ktb.moyeota.domain.taxipot.message.MatchingMessages;
import com.ktb.moyeota.global.event.EventEnvelope;
import com.ktb.moyeota.global.event.EventEnvelopeFactory;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Map;
import java.util.Properties;
import org.apache.kafka.common.serialization.ByteArrayDeserializer;
import org.apache.kafka.common.serialization.ByteArraySerializer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.apache.kafka.streams.StreamsBuilder;
import org.apache.kafka.streams.StreamsConfig;
import org.apache.kafka.streams.TestInputTopic;
import org.apache.kafka.streams.TestOutputTopic;
import org.apache.kafka.streams.TopologyTestDriver;
import org.apache.kafka.streams.errors.LogAndContinueExceptionHandler;
import org.apache.kafka.streams.errors.LogAndContinueProcessingExceptionHandler;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.jackson.autoconfigure.JacksonAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.support.serializer.JacksonJsonDeserializer;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

@SpringBootTest(classes = MatchingBadMessageTest.class)
@ImportAutoConfiguration(JacksonAutoConfiguration.class)
class MatchingBadMessageTest {

    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");
    private static final Instant START = LocalDateTime.of(2026, 10, 28, 9, 0).atZone(SEOUL).toInstant();
    private static final LocalDateTime DEPARTURE_AT = LocalDateTime.of(2026, 10, 28, 9, 30);
    private static final Duration SWEEP = Duration.ofSeconds(5);
    private static final String PANGYO = "2079:5606";
    private static final String DLT = MatchingMessages.REQUEST_TOPIC + "-dlt";

    @Autowired
    private JsonMapper jsonMapper;

    private TopologyTestDriver driver;
    private TestInputTopic<String, byte[]> requests;
    private TestOutputTopic<String, EventEnvelope<MatchDecidedData>> decisions;
    private TestOutputTopic<byte[], byte[]> deadLetters;

    @BeforeEach
    void setUp() {
        EventEnvelopeFactory envelopeFactory = new EventEnvelopeFactory(Clock.fixed(START, SEOUL));
        StreamsBuilder builder = new StreamsBuilder();
        MatchingTopology.define(builder, jsonMapper, () -> new RegionMatchingProcessor(
                new MatchingRules(100, 4), 8, SWEEP, SEOUL, envelopeFactory, jsonMapper, new SimpleMeterRegistry()));
        Properties config = new Properties();
        config.put(StreamsConfig.APPLICATION_ID_CONFIG, "moyeota-matching");
        config.put(StreamsConfig.BOOTSTRAP_SERVERS_CONFIG, "dummy:9092");
        config.put(StreamsConfig.DESERIALIZATION_EXCEPTION_HANDLER_CLASS_CONFIG,
                LogAndContinueExceptionHandler.class.getName());
        config.put(StreamsConfig.PROCESSING_EXCEPTION_HANDLER_CLASS_CONFIG,
                LogAndContinueProcessingExceptionHandler.class.getName());
        config.put(StreamsConfig.ERRORS_DEAD_LETTER_QUEUE_TOPIC_NAME_CONFIG, DLT);
        driver = new TopologyTestDriver(builder.build(), config, START);

        requests = driver.createInputTopic(MatchingMessages.REQUEST_TOPIC,
                new StringSerializer(), new ByteArraySerializer());
        JacksonJsonDeserializer<EventEnvelope<MatchDecidedData>> deserializer = new JacksonJsonDeserializer<>(
                new TypeReference<EventEnvelope<MatchDecidedData>>() {
                }, jsonMapper);
        deserializer.setUseTypeHeaders(false);
        decisions = driver.createOutputTopic(MatchingMessages.DECISION_TOPIC, new StringDeserializer(), deserializer);
        deadLetters = driver.createOutputTopic(DLT, new ByteArrayDeserializer(), new ByteArrayDeserializer());
    }

    @AfterEach
    void tearDown() {
        driver.close();
    }

    @Test
    @DisplayName("JSON 이 아닌 메시지는 DLT 로 보내고 다음 요청을 이어서 묶는다")
    void skipsUnreadableMessage() {
        requests.pipeInput(PANGYO, "not json".getBytes(StandardCharsets.UTF_8));
        requested(101);
        requested(102);

        driver.advanceWallClockTime(SWEEP);

        assertThat(decisions.readValue().data().resultId()).isEqualTo("101,102");
        assertThat(deadLetters.getQueueSize()).isEqualTo(1);
    }

    @Test
    @DisplayName("출발지가 빠진 요청은 상태에 넣지 않고 DLT 로 보내, 주기가 멈추지 않는다")
    void skipsRequestMissingPlace() {
        send(Map.of("request_id", 103, "user_id", 103, "departure_at", DEPARTURE_AT.toString(),
                "requested_at", DEPARTURE_AT.minusMinutes(30).toString()));
        requested(101);
        requested(102);

        driver.advanceWallClockTime(SWEEP);

        assertThat(decisions.readValue().data().resultId()).isEqualTo("101,102");
        assertThat(deadLetters.getQueueSize()).isEqualTo(1);
    }

    private void requested(long requestId) {
        send(new MatchRequestData(requestId, requestId, place(37_394_776), place(37_498_086),
                DEPARTURE_AT, DEPARTURE_AT.minusMinutes(30)));
    }

    private void send(Object data) {
        requests.pipeInput(PANGYO, jsonMapper.writeValueAsBytes(new EventEnvelope<>(
                MatchingMessages.REQUESTED + "-" + System.nanoTime(), MatchingMessages.REQUESTED,
                MatchingMessages.SCHEMA_VERSION, START.atZone(SEOUL).toOffsetDateTime(), data)));
    }

    private static MatchPlaceData place(long latMicros) {
        return new MatchPlaceData("장소", BigDecimal.valueOf(latMicros, 6), BigDecimal.valueOf(127_111_158, 6));
    }
}
