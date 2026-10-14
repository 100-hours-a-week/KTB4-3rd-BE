package com.ktb.moyeota.domain.settlement.service;

import com.ktb.moyeota.domain.image.service.ImageReadUrlIssuer;
import com.ktb.moyeota.domain.settlement.config.TransferAnalysisProperties;
import com.ktb.moyeota.domain.settlement.event.TransferAnalysisRequestedEvent;
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
public class TransferAnalysisService {

    static final String QUEUE_FULL = "QUEUE_FULL";
    static final String QUEUE_WAIT_EXCEEDED = "QUEUE_WAIT_EXCEEDED";

    private final TransferAnalysisResultWriter resultWriter;
    private final ImageReadUrlIssuer imageReadUrlIssuer;
    private final AiAnalysisClient aiAnalysisClient;
    private final UserEventSender userEventSender;
    private final TransferAnalysisProperties properties;
    private final MeterRegistry meterRegistry;
    private final Clock clock;

    public void analyze(TransferAnalysisRequestedEvent request) {
        Duration waited = Duration.between(request.requestedAt(), LocalDateTime.now(clock));
        meterRegistry.timer("settlement.transfer.analysis.queue.wait").record(waited);
        if (waited.compareTo(properties.queueWaitLimit()) > 0) {
            AnalysisOutcome<Integer> outcome = new AnalysisOutcome.SystemFailure<>(QUEUE_WAIT_EXCEEDED);
            finish(request, outcome, resultWriter.write(request, outcome));
            return;
        }

        String imageUrl = imageReadUrlIssuer.issue(request.transferImageKey());
        Timer.Sample sample = Timer.start(meterRegistry);
        AnalysisOutcome<Integer> outcome = aiAnalysisClient.readTransfer(imageUrl);
        sample.stop(meterRegistry.timer("settlement.transfer.analysis.ai", "result", resultOf(outcome)));
        finish(request, outcome, resultWriter.write(request, outcome));
    }

    public void rejectBusy(TransferAnalysisRequestedEvent request) {
        AnalysisOutcome<Integer> outcome = new AnalysisOutcome.SystemFailure<>(QUEUE_FULL);
        finish(request, outcome, resultWriter.writeInNewTransaction(request, outcome));
    }

    private void finish(TransferAnalysisRequestedEvent request, AnalysisOutcome<Integer> outcome,
                        Optional<TransferAnalysisResultWriter.Delivery> delivery) {
        meterRegistry.counter("settlement.transfer.analysis", "result", resultOf(outcome), "code", codeOf(outcome))
                .increment();
        log.info("[TRANSFER_ANALYSIS_FINISHED] settlementId={} userId={} attempt={} result={} code={} applied={}",
                request.settlementId(), request.userId(), request.attempt(), resultOf(outcome), codeOf(outcome),
                delivery.isPresent());
        delivery.ifPresent(d -> d.recipientIds()
                .forEach(recipientId -> userEventSender.send(recipientId, UserChannel.SETTLEMENT, d.message())));
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
