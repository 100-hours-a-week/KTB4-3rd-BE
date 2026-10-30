package com.ktb.moyeota.domain.taxipot.stream;

import com.ktb.moyeota.domain.taxipot.matching.MatchResult;
import com.ktb.moyeota.domain.taxipot.matching.MatchingRules;
import com.ktb.moyeota.domain.taxipot.message.MatchDecidedData;
import com.ktb.moyeota.domain.taxipot.message.MatchingMessages;
import com.ktb.moyeota.global.event.EventEnvelope;
import com.ktb.moyeota.global.event.EventEnvelopeFactory;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.streams.processor.PunctuationType;
import org.apache.kafka.streams.processor.api.Processor;
import org.apache.kafka.streams.processor.api.ProcessorContext;
import org.apache.kafka.streams.processor.api.Record;
import org.apache.kafka.streams.state.KeyValueStore;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

@Slf4j
public class RegionMatchingProcessor
        implements Processor<String, EventEnvelope<JsonNode>, String, EventEnvelope<MatchDecidedData>> {

    private static final String ENGINE = "streams";

    private final CellEvents events;
    private final CellSweep cellSweep;
    private final Duration sweepDelay;
    private final ZoneId zone;
    private final EventEnvelopeFactory envelopeFactory;
    private final Timer eventTimer;
    private final Timer sweepTimer;

    private ProcessorContext<String, EventEnvelope<MatchDecidedData>> context;
    private KeyValueStore<String, CellRequest> requests;
    private KeyValueStore<String, CellPot> pots;

    public RegionMatchingProcessor(
            MatchingRules rules, int neighborLimit, Duration sweepDelay, ZoneId zone,
            EventEnvelopeFactory envelopeFactory, JsonMapper jsonMapper, MeterRegistry meterRegistry) {
        this.events = new CellEvents(jsonMapper);
        this.cellSweep = new CellSweep(rules, neighborLimit);
        this.sweepDelay = sweepDelay;
        this.zone = zone;
        this.envelopeFactory = envelopeFactory;
        this.eventTimer = meterRegistry.timer(MatchingMetrics.EVENT, MatchingMetrics.ENGINE_TAG, ENGINE);
        this.sweepTimer = meterRegistry.timer(MatchingMetrics.SWEEP, MatchingMetrics.ENGINE_TAG, ENGINE);
    }

    @Override
    public void init(ProcessorContext<String, EventEnvelope<MatchDecidedData>> context) {
        this.context = context;
        this.requests = context.getStateStore(MatchingTopology.REQUEST_STORE);
        this.pots = context.getStateStore(MatchingTopology.POT_STORE);
        context.schedule(sweepDelay, PunctuationType.WALL_CLOCK_TIME, this::sweep);
    }

    @Override
    public void process(Record<String, EventEnvelope<JsonNode>> record) {
        eventTimer.record(() -> apply(record));
    }

    private void apply(Record<String, EventEnvelope<JsonNode>> record) {
        EventEnvelope<JsonNode> envelope = record.value();
        if (!events.apply(envelope, new StoreCellEntries(requests, pots, record.key()))) {
            log.warn("[KAFKA_MATCHING_UNKNOWN_EVENT] eventId={}, eventType={}",
                    envelope.eventId(), envelope.eventType());
        }
    }

    private void sweep(long timestamp) {
        sweepTimer.record(() -> sweepAt(timestamp));
    }

    private void sweepAt(long timestamp) {
        LocalDateTime cutoff = LocalDateTime.ofInstant(Instant.ofEpochMilli(timestamp), zone)
                .truncatedTo(ChronoUnit.MINUTES);
        StoreCellEntries.views(requests, pots).forEach((region, view) -> {
            for (MatchResult result : cellSweep.sweep(view, cutoff, new StoreCellEntries(requests, pots, region))) {
                context.forward(new Record<>(region, envelopeFactory.create(
                        MatchingMessages.DECIDED, MatchingMessages.SCHEMA_VERSION, MatchDecidedData.from(result)),
                        timestamp));
                log.info("[KAFKA_MATCHING] region={}, resultId={}", region, result.resultId());
            }
        });
    }
}
