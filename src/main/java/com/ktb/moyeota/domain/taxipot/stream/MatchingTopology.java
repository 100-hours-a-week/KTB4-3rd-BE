package com.ktb.moyeota.domain.taxipot.stream;

import com.ktb.moyeota.domain.taxipot.message.MatchDecidedData;
import com.ktb.moyeota.domain.taxipot.message.MatchingMessages;
import com.ktb.moyeota.global.event.EventEnvelope;
import org.apache.kafka.common.serialization.Serdes;
import org.apache.kafka.streams.StreamsBuilder;
import org.apache.kafka.streams.kstream.Consumed;
import org.apache.kafka.streams.kstream.KStream;
import org.apache.kafka.streams.kstream.Produced;
import org.apache.kafka.streams.processor.api.ProcessorSupplier;
import org.apache.kafka.streams.state.Stores;
import org.springframework.kafka.support.serializer.JacksonJsonSerde;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

public final class MatchingTopology {

    public static final String REQUEST_STORE = "matching-cell-requests";
    public static final String POT_STORE = "matching-cell-pots";

    private MatchingTopology() {
    }

    public static KStream<String, EventEnvelope<MatchDecidedData>> define(
            StreamsBuilder builder, JsonMapper jsonMapper,
            ProcessorSupplier<String, EventEnvelope<JsonNode>, String, EventEnvelope<MatchDecidedData>> processor) {
        builder.addStateStore(Stores.keyValueStoreBuilder(Stores.inMemoryKeyValueStore(REQUEST_STORE),
                Serdes.String(), serde(new TypeReference<CellRequest>() {
                }, jsonMapper)));
        builder.addStateStore(Stores.keyValueStoreBuilder(Stores.inMemoryKeyValueStore(POT_STORE),
                Serdes.String(), serde(new TypeReference<CellPot>() {
                }, jsonMapper)));
        KStream<String, EventEnvelope<MatchDecidedData>> decisions = builder
                .stream(MatchingMessages.REQUEST_TOPIC, Consumed.with(Serdes.String(),
                        serde(new TypeReference<EventEnvelope<JsonNode>>() {
                        }, jsonMapper)))
                .process(processor, REQUEST_STORE, POT_STORE);
        decisions.to(MatchingMessages.DECISION_TOPIC, Produced.with(Serdes.String(),
                serde(new TypeReference<EventEnvelope<MatchDecidedData>>() {
                }, jsonMapper)));
        return decisions;
    }

    private static <T> JacksonJsonSerde<T> serde(TypeReference<T> type, JsonMapper jsonMapper) {
        return new JacksonJsonSerde<T>(type, jsonMapper).noTypeInfo().ignoreTypeHeaders();
    }
}
