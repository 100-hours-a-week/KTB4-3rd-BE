package com.ktb.moyeota.domain.congestion.batch;

import com.ktb.moyeota.domain.congestion.config.CongestionBatchProperties;
import com.ktb.moyeota.domain.congestion.repository.CongestionJobRepository;
import com.ktb.moyeota.domain.congestion.repository.SpotRepository;
import com.ktb.moyeota.domain.congestion.window.CongestionWindow;
import com.ktb.moyeota.domain.congestion.window.CongestionWindowCalculator;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class CongestionWindowCloser {

    private final CongestionWindowCalculator calculator;
    private final CongestionJobRepository jobRepository;
    private final SpotRepository spotRepository;
    private final CongestionAnalysisRequester requester;
    private final CongestionBatchProperties properties;
    private final Clock clock;

    public void closeEndedWindows() {
        Instant latestEnd = calculator.windowContaining(clock.instant().minus(properties.grace()))
                .start().toInstant();
        Optional<Instant> lastEnd = jobRepository.findLastWindowEnd();
        if (lastEnd.isEmpty()) {
            closeFirstWindow(latestEnd);
            return;
        }
        Instant from = lastEnd.get();
        while (true) {
            CongestionWindow window = calculator.windowContaining(from);
            Instant end = window.end().toInstant();
            if (end.isAfter(latestEnd) || !jobRepository.advance(from, end)) {
                return;
            }
            request(window);
            from = end;
        }
    }

    private void closeFirstWindow(Instant latestEnd) {
        CongestionWindow window = calculator.windowContaining(latestEnd.minusNanos(1));
        if (jobRepository.start(latestEnd)) {
            request(window);
        }
    }

    private void request(CongestionWindow window) {
        List<Long> spotIds = spotRepository.findIdsSignaledSince(window.start().toInstant());
        if (!spotIds.isEmpty()) {
            requester.request(window, spotIds);
        }
    }
}
