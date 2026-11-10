package com.ktb.moyeota.domain.settlement.service;

import com.ktb.moyeota.domain.image.service.ImageReadUrlIssuer;
import com.ktb.moyeota.domain.settlement.config.MeterAnalysisProperties;
import com.ktb.moyeota.domain.settlement.event.MeterAnalysisRequestedEvent;
import com.ktb.moyeota.global.external.ai.AiAnalysisClient;
import com.ktb.moyeota.global.external.ai.AnalysisOutcome;
import com.ktb.moyeota.global.websocket.UserChannel;
import com.ktb.moyeota.global.websocket.UserEventSender;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class MeterAnalysisService {

    static final String QUEUE_FULL = "QUEUE_FULL";
    static final String QUEUE_WAIT_EXCEEDED = "QUEUE_WAIT_EXCEEDED";
    static final String COST_OUT_OF_RANGE = "COST_OUT_OF_RANGE";

    private static final int MIN_AMOUNT = 1;
    private static final int MAX_AMOUNT = 999_999;

    private final MeterAnalysisResultWriter resultWriter;
    private final ImageReadUrlIssuer imageReadUrlIssuer;
    private final AiAnalysisClient aiAnalysisClient;
    private final UserEventSender userEventSender;
    private final MeterAnalysisProperties properties;
    private final MeterRegistry meterRegistry;
    private final Clock clock;

    public void analyze(MeterAnalysisRequestedEvent request) {
        Duration waited = Duration.between(request.startedAt(), LocalDateTime.now(clock));
        meterRegistry.timer("settlement.meter.analysis.queue.wait").record(waited);
        if (waited.compareTo(properties.queueWaitLimit()) > 0) {
            apply(request.settlementId(), request.startedAt(), new AnalysisOutcome.SystemFailure<>(QUEUE_WAIT_EXCEEDED));
            return;
        }

        String imageUrl = imageReadUrlIssuer.issue(request.meterImageKey());
        Timer.Sample sample = Timer.start(meterRegistry);
        AnalysisOutcome<Integer> read = aiAnalysisClient.readMeter(imageUrl);
        sample.stop(meterRegistry.timer("settlement.meter.analysis.ai", "result", resultOf(read)));
        apply(request.settlementId(), request.startedAt(), read);
    }

    public void apply(Long settlementId, LocalDateTime startedAt, AnalysisOutcome<Integer> analyzed) {
        AnalysisOutcome<Integer> outcome = withinAmountRange(analyzed);
        finish(settlementId, startedAt, outcome, resultWriter.write(settlementId, startedAt, outcome));
    }

    public void rejectBusy(MeterAnalysisRequestedEvent request) {
        AnalysisOutcome<Integer> outcome = new AnalysisOutcome.SystemFailure<>(QUEUE_FULL);
        finish(request.settlementId(), request.startedAt(), outcome,
                resultWriter.writeInNewTransaction(request.settlementId(), request.startedAt(), outcome));
    }

    private void finish(Long settlementId, LocalDateTime startedAt, AnalysisOutcome<Integer> outcome,
                        Optional<MeterAnalysisResultWriter.Delivery> delivery) {
        meterRegistry.counter("settlement.meter.analysis",
                        "result", resultOf(outcome),
                        "code", codeOf(outcome),
                        "applied", String.valueOf(delivery.isPresent()))
                .increment();
        log.info("[METER_ANALYSIS_FINISHED] settlementId={} startedAt={} result={} code={} applied={}",
                settlementId, startedAt, resultOf(outcome), codeOf(outcome), delivery.isPresent());
        delivery.ifPresent(d -> userEventSender.send(d.hostId(), UserChannel.SETTLEMENT, d.message()));
    }

    private static AnalysisOutcome<Integer> withinAmountRange(AnalysisOutcome<Integer> outcome) {
        if (outcome instanceof AnalysisOutcome.Read<Integer> read
                && (read.result() < MIN_AMOUNT || read.result() > MAX_AMOUNT)) {
            return new AnalysisOutcome.Unreadable<>(COST_OUT_OF_RANGE);
        }
        return outcome;
    }

    private static String resultOf(AnalysisOutcome<?> outcome) {
        return switch (outcome) {
            case AnalysisOutcome.Read<?> read -> "READ";
            case AnalysisOutcome.Unreadable<?> unreadable -> "UNREADABLE";
            case AnalysisOutcome.SystemFailure<?> failure -> "SYSTEM_FAILURE";
        };
    }

    private static String codeOf(AnalysisOutcome<?> outcome) {
        return switch (outcome) {
            case AnalysisOutcome.Read<?> read -> "NONE";
            case AnalysisOutcome.Unreadable<?> unreadable -> unreadable.code();
            case AnalysisOutcome.SystemFailure<?> failure -> failure.code();
        };
    }
}
