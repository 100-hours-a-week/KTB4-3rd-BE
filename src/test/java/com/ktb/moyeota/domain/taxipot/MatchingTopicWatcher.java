package com.ktb.moyeota.domain.taxipot;

import com.ktb.moyeota.domain.taxipot.message.MatchingMessages;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;
import org.apache.kafka.clients.admin.Admin;
import org.apache.kafka.clients.admin.AdminClientConfig;
import org.apache.kafka.clients.admin.OffsetSpec;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.consumer.OffsetAndMetadata;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.errors.WakeupException;
import org.apache.kafka.common.serialization.StringDeserializer;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

class MatchingTopicWatcher implements AutoCloseable {

    private final KafkaConsumer<String, String> consumer;
    private final JsonMapper jsonMapper;
    private final List<ConsumerRecord<String, String>> records = new CopyOnWriteArrayList<>();
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    MatchingTopicWatcher(String bootstrap, JsonMapper jsonMapper) {
        this.jsonMapper = jsonMapper;
        this.consumer = new KafkaConsumer<>(Map.of(
                ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrap,
                ConsumerConfig.GROUP_ID_CONFIG, "matching-topic-watcher-" + UUID.randomUUID(),
                ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest",
                ConsumerConfig.ISOLATION_LEVEL_CONFIG, "read_committed",
                ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class,
                ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class));
        executor.submit(this::poll);
    }

    static boolean consumedToEnd(String bootstrap, String groupId) {
        try (Admin admin = Admin.create(Map.of(AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrap))) {
            Map<TopicPartition, OffsetAndMetadata> committed = admin.listConsumerGroupOffsets(groupId)
                    .partitionsToOffsetAndMetadata().get(10, TimeUnit.SECONDS);
            Map<TopicPartition, OffsetSpec> latest = committed.keySet().stream()
                    .collect(Collectors.toMap(partition -> partition, partition -> OffsetSpec.latest()));
            Map<TopicPartition, Long> ends = admin.listOffsets(latest).all().get(10, TimeUnit.SECONDS)
                    .entrySet().stream()
                    .collect(Collectors.toMap(Map.Entry::getKey, entry -> entry.getValue().offset()));
            return !committed.isEmpty() && committed.entrySet().stream()
                    .allMatch(entry -> entry.getValue().offset() >= ends.get(entry.getKey()));
        } catch (Exception e) {
            return false;
        }
    }

    List<ConsumerRecord<String, String>> decisionsWith(long requestId) {
        return records.stream()
                .filter(record -> record.topic().equals(MatchingMessages.DECISION_TOPIC))
                .filter(record -> ids(data(record).path("requests"), "request_id").contains(requestId))
                .toList();
    }

    List<ConsumerRecord<String, String>> rejectionsWith(long requestId) {
        return records.stream()
                .filter(record -> record.topic().equals(MatchingMessages.REQUEST_TOPIC))
                .filter(record -> MatchingMessages.REJECTED.equals(envelope(record).path("event_type").asString()))
                .filter(record -> ids(data(record).path("request_ids"), null).contains(requestId))
                .toList();
    }

    Set<String> rejectionEventIdsWith(long requestId) {
        return rejectionsWith(requestId).stream()
                .map(record -> envelope(record).path("event_id").asString())
                .collect(Collectors.toSet());
    }

    @Override
    public void close() {
        consumer.wakeup();
        executor.shutdown();
    }

    private void poll() {
        try (consumer) {
            consumer.subscribe(List.of(MatchingMessages.REQUEST_TOPIC, MatchingMessages.DECISION_TOPIC));
            while (true) {
                consumer.poll(Duration.ofMillis(200)).forEach(records::add);
            }
        } catch (WakeupException ignored) {
        }
    }

    private JsonNode envelope(ConsumerRecord<String, String> record) {
        return jsonMapper.readTree(record.value());
    }

    private JsonNode data(ConsumerRecord<String, String> record) {
        return envelope(record).path("data");
    }

    private static Set<Long> ids(JsonNode array, String field) {
        return StreamSupport.stream(array.spliterator(), false)
                .map(node -> field == null ? node.asLong() : node.path(field).asLong())
                .collect(Collectors.toSet());
    }
}
