package com.ktb.moyeota.domain.congestion.service;

import com.ktb.moyeota.domain.congestion.cache.CachedSpot;
import com.ktb.moyeota.domain.congestion.cache.CongestionLatestCache;
import com.ktb.moyeota.domain.congestion.document.CauseReport;
import com.ktb.moyeota.domain.congestion.document.CongestionHistory;
import com.ktb.moyeota.domain.congestion.document.CongestionLatest;
import com.ktb.moyeota.domain.congestion.document.Spot;
import com.ktb.moyeota.domain.congestion.message.AnalysisStatus;
import com.ktb.moyeota.domain.congestion.message.CongestionAnalysis;
import com.ktb.moyeota.domain.congestion.message.CongestionAnalysisResultData;
import com.ktb.moyeota.domain.congestion.repository.CongestionHistoryRepository;
import com.ktb.moyeota.domain.congestion.repository.SpotRepository;
import com.ktb.moyeota.domain.congestion.status.CongestionStatus;
import com.ktb.moyeota.domain.congestion.status.CongestionStatusMapper;
import com.ktb.moyeota.domain.congestion.window.CongestionWindow;
import com.ktb.moyeota.domain.congestion.window.CongestionWindowCalculator;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class CongestionResultService {

    private final SpotRepository spotRepository;
    private final CongestionHistoryRepository historyRepository;
    private final CongestionStatusMapper statusMapper;
    private final CongestionWindowCalculator calculator;
    private final CongestionLatestCache latestCache;

    public ResultApplyOutcome apply(CongestionAnalysisResultData data) {
        Optional<Spot> spot = spotRepository.findById(data.spotId());
        if (spot.isEmpty()) {
            return ResultApplyOutcome.UNKNOWN_SPOT;
        }
        Instant windowEnd = data.windowEnd().toInstant();
        Instant calculatedAt = spot.get().getCalculatedAt();
        if (windowEnd.equals(calculatedAt)) {
            refreshCache(data.spotId());
            return ResultApplyOutcome.ALREADY_APPLIED;
        }
        if (calculatedAt != null && windowEnd.isBefore(calculatedAt)) {
            return ResultApplyOutcome.STALE;
        }
        if (data.analysisStatus() == AnalysisStatus.FAILED) {
            spotRepository.advanceCalculatedAt(data.spotId(), windowEnd);
            refreshCache(data.spotId());
            return ResultApplyOutcome.ANALYSIS_FAILED;
        }

        CongestionAnalysis analysis = data.result();
        CongestionStatus status = statusMapper.statusOf(analysis.toReport()).orElse(null);
        List<CauseReport> causes = analysis.causes().stream()
                .map(cause -> new CauseReport(cause.causeCode(), cause.reportCount()))
                .toList();
        if (!Objects.equals(status, spot.get().getStatus())) {
            CongestionWindow window = calculator.windowContaining(windowEnd.minusNanos(1));
            int windowMinutes = (int) Duration.between(window.start(), window.end()).toMinutes();
            historyRepository.append(CongestionHistory.of(data.spotId(), windowEnd, windowMinutes, status,
                    analysis.congestionLevel(), causes, analysis.summary(), analysis.reportCount()));
        }
        Instant nextWindowEnd = calculator.windowContaining(windowEnd).end().toInstant();
        Instant validUntil = calculator.windowContaining(nextWindowEnd).end().toInstant();
        boolean applied = spotRepository.applyLatest(data.spotId(), new CongestionLatest(status,
                analysis.congestionLevel(), causes, analysis.summary(), analysis.reportCount(), windowEnd, validUntil));
        if (!applied) {
            return ResultApplyOutcome.STALE;
        }
        refreshCache(data.spotId());
        return ResultApplyOutcome.APPLIED;
    }

    private void refreshCache(Long spotId) {
        spotRepository.findById(spotId).map(CachedSpot::from).ifPresent(latestCache::put);
    }
}
