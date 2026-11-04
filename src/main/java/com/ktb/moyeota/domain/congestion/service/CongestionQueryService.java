package com.ktb.moyeota.domain.congestion.service;

import com.ktb.moyeota.domain.congestion.cache.CachedSpot;
import com.ktb.moyeota.domain.congestion.cache.CongestionLatestCache;
import com.ktb.moyeota.domain.congestion.error.CongestionErrorCode;
import com.ktb.moyeota.domain.congestion.repository.SpotRepository;
import com.ktb.moyeota.domain.congestion.status.CongestionStatus;
import com.ktb.moyeota.domain.congestion.window.CongestionWindowCalculator;
import com.ktb.moyeota.global.exception.BusinessException;
import java.time.Clock;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class CongestionQueryService {

    private final CongestionLatestCache latestCache;
    private final SpotRepository spotRepository;
    private final CongestionWindowCalculator calculator;
    private final Clock clock;

    public CongestionSpotDetail findSpot(Long spotId) {
        CachedSpot spot = latestCache.get(spotId)
                .or(() -> spotRepository.findById(spotId).map(CachedSpot::from))
                .orElseThrow(() -> new BusinessException(CongestionErrorCode.SPOT_NOT_FOUND));
        Instant now = clock.instant();
        boolean visible = spot.validUntil() != null && now.isBefore(spot.validUntil());
        CongestionStatus status = visible ? spot.status() : null;
        return new CongestionSpotDetail(
                spot.id(),
                spot.type(),
                spot.name(),
                spot.lat(),
                spot.lng(),
                status,
                visible ? spot.resultWindowEnd() : null,
                reasonOf(spot, status),
                isAnalyzing(spot, now));
    }

    private static String reasonOf(CachedSpot spot, CongestionStatus status) {
        if (status == null || status == CongestionStatus.SMOOTH || spot.causes().isEmpty()) {
            return null;
        }
        return spot.summary();
    }

    private boolean isAnalyzing(CachedSpot spot, Instant now) {
        Instant pending = spot.pendingWindowEnd();
        if (pending == null || (spot.calculatedAt() != null && !pending.isAfter(spot.calculatedAt()))) {
            return false;
        }
        return now.isBefore(calculator.windowContaining(pending).end().toInstant());
    }
}
