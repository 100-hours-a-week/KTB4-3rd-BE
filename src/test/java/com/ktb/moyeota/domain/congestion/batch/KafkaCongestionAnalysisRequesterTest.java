package com.ktb.moyeota.domain.congestion.batch;

import static org.assertj.core.api.Assertions.assertThat;

import com.ktb.moyeota.domain.congestion.document.Signal;
import com.ktb.moyeota.domain.congestion.message.CongestionMessages;
import com.ktb.moyeota.domain.congestion.repository.SignalRepository;
import com.ktb.moyeota.domain.congestion.repository.SpotRepository;
import com.ktb.moyeota.domain.congestion.window.CongestionWindow;
import com.ktb.moyeota.global.config.ClockConfig;
import com.ktb.moyeota.global.config.KafkaTestContainerConfig;
import com.ktb.moyeota.global.config.MongoTestContainerConfig;
import com.ktb.moyeota.global.event.EventEnvelopeFactory;
import java.time.OffsetDateTime;
import java.util.Date;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.bson.Document;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.data.mongodb.autoconfigure.DataMongoAutoConfiguration;
import org.springframework.boot.jackson.autoconfigure.JacksonAutoConfiguration;
import org.springframework.boot.kafka.autoconfigure.KafkaAutoConfiguration;
import org.springframework.boot.mongodb.autoconfigure.MongoAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnectionAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.kafka.annotation.KafkaListener;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

@SpringBootTest(
        classes = {KafkaCongestionAnalysisRequester.class, PostBundleReader.class, AuthorKeyHasher.class,
                SignalRepository.class, SpotRepository.class, EventEnvelopeFactory.class, ClockConfig.class,
                KafkaTestContainerConfig.class, MongoTestContainerConfig.class,
                KafkaCongestionAnalysisRequesterTest.ListenerConfig.class},
        properties = "spring.kafka.consumer.auto-offset-reset=earliest")
@ImportAutoConfiguration({
        JacksonAutoConfiguration.class,
        KafkaAutoConfiguration.class,
        MongoAutoConfiguration.class,
        DataMongoAutoConfiguration.class,
        ServiceConnectionAutoConfiguration.class})
class KafkaCongestionAnalysisRequesterTest {

    private static final Long PANGYO = 101L;
    private static final Long JEONGJA = 102L;
    private static final CongestionWindow WINDOW = new CongestionWindow(
            OffsetDateTime.parse("2026-11-02T07:00:00+09:00"), OffsetDateTime.parse("2026-11-02T07:10:00+09:00"));

    @Autowired
    private KafkaCongestionAnalysisRequester requester;

    @Autowired
    private SignalRepository signalRepository;

    @Autowired
    private MongoTemplate mongoTemplate;

    @Autowired
    private JsonMapper jsonMapper;

    @Autowired
    private BlockingQueue<ConsumerRecord<String, String>> requests;

    @BeforeEach
    void setUp() {
        mongoTemplate.dropCollection("spots");
        mongoTemplate.dropCollection("signals");
        requests.clear();
        mongoTemplate.getCollection("spots").insertMany(List.of(
                new Document("_id", PANGYO).append("type", "STATION").append("name", "판교역"),
                new Document("_id", JEONGJA).append("type", "STATION").append("name", "정자역")));
    }

    @Test
    @DisplayName("글이 있는 스팟마다 봉투에 담은 분석 요청을 스팟 번호 키로 보낸다")
    void publishesRequestPerSpot() throws Exception {
        signalRepository.insertIfAbsent(Signal.ofPost(1L, PANGYO, "10",
                OffsetDateTime.parse("2026-11-02T07:05:00+09:00").toInstant(), "판교역 혼잡", "사람 많아요"));

        requester.request(WINDOW, List.of(PANGYO, JEONGJA));

        ConsumerRecord<String, String> record = requests.poll(30, TimeUnit.SECONDS);
        assertThat(record).isNotNull();
        assertThat(record.key()).isEqualTo("101");
        JsonNode envelope = jsonMapper.readTree(record.value());
        assertThat(envelope.get("event_type").asString()).isEqualTo(CongestionMessages.ANALYSIS_REQUESTED);
        assertThat(envelope.get("schema_version").asInt()).isEqualTo(1);
        JsonNode data = envelope.get("data");
        assertThat(data.get("spot_id").asLong()).isEqualTo(PANGYO);
        assertThat(OffsetDateTime.parse(data.get("window_end").asString())).isEqualTo(WINDOW.end());
        JsonNode bundle = data.get("post_bundles").get(0);
        assertThat(bundle.get("post_id").asLong()).isEqualTo(1L);
        assertThat(bundle.get("context_only").asBoolean()).isFalse();
        assertThat(bundle.get("author_key").asString()).hasSize(64);
        assertThat(requests.poll(3, TimeUnit.SECONDS)).isNull();
    }

    @Test
    @DisplayName("보낸 스팟만 보낸 구간 끝을 분석 중으로 적는다")
    void marksOnlySentSpotsAnalyzing() throws Exception {
        signalRepository.insertIfAbsent(Signal.ofPost(2L, PANGYO, "10",
                OffsetDateTime.parse("2026-11-02T07:05:00+09:00").toInstant(), "판교역 혼잡", "사람 많아요"));

        requester.request(WINDOW, List.of(PANGYO, JEONGJA));

        Document pangyo = spot(PANGYO);
        assertThat(pangyo.getDate("pending_window_end"))
                .isEqualTo(Date.from(WINDOW.end().toInstant()));
        assertThat(pangyo.containsKey("pending_until")).isFalse();
        assertThat(spot(JEONGJA).containsKey("pending_window_end")).isFalse();
    }

    private Document spot(Long id) {
        return mongoTemplate.getCollection("spots").find(new Document("_id", id)).first();
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class ListenerConfig {

        @Bean
        NewTopic requestTopic() {
            return new NewTopic(CongestionMessages.REQUEST_TOPIC, 1, (short) 1);
        }

        @Bean
        BlockingQueue<ConsumerRecord<String, String>> requests() {
            return new LinkedBlockingQueue<>();
        }

        @Bean
        RequestListener requestListener(BlockingQueue<ConsumerRecord<String, String>> requests) {
            return new RequestListener(requests);
        }
    }

    static class RequestListener {

        private final BlockingQueue<ConsumerRecord<String, String>> requests;

        RequestListener(BlockingQueue<ConsumerRecord<String, String>> requests) {
            this.requests = requests;
        }

        @KafkaListener(topics = CongestionMessages.REQUEST_TOPIC, groupId = "congestion-request-test")
        void listen(ConsumerRecord<String, String> record) {
            requests.add(record);
        }
    }
}
