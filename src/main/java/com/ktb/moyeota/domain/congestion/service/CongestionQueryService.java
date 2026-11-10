package com.ktb.moyeota.domain.congestion.service;

import com.ktb.moyeota.domain.congestion.cache.CachedSpot;
import com.ktb.moyeota.domain.congestion.cache.CongestionLatestCache;
import com.ktb.moyeota.domain.congestion.dto.CongestionSpotItem;
import com.ktb.moyeota.domain.congestion.dto.CongestionSpotSearchRequest;
import com.ktb.moyeota.domain.congestion.dto.CongestionSpotSearchResponse;
import com.ktb.moyeota.domain.congestion.error.CongestionErrorCode;
import com.ktb.moyeota.domain.congestion.repository.SpotRepository;
import com.ktb.moyeota.domain.congestion.status.CongestionStatus;
import com.ktb.moyeota.domain.congestion.window.CongestionWindowCalculator;
import com.ktb.moyeota.global.common.Viewport;
import com.ktb.moyeota.global.exception.BusinessException;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class CongestionQueryService {

    private static final int PIN_LIMIT = 500;

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

    public CongestionSpotSearchResponse findSpotsInBounds(CongestionSpotSearchRequest request) {
        Viewport viewport = new Viewport(request.swLat(), request.swLng(), request.neLat(), request.neLng());
        Instant now = clock.instant();
        List<CongestionSpotItem> items = latestSpots().stream()
                .filter(spot -> spot.status() != null)
                .filter(spot -> spot.validUntil() != null && spot.validUntil().isAfter(now))
                .filter(spot -> contains(viewport, spot))
                .sorted(Comparator.comparing(CachedSpot::id))
                .map(CongestionSpotItem::from)
                .toList();
        return new CongestionSpotSearchResponse(items, PIN_LIMIT, false);
    }

    private List<CachedSpot> latestSpots() {
        Optional<List<CachedSpot>> cached = latestCache.findAll();
        if (cached.isPresent() && !cached.get().isEmpty()) {
            return cached.get();
        }
        List<CachedSpot> loaded = spotRepository.findAllLatest().stream()
                .map(CachedSpot::from)
                .toList();
        if (cached.isPresent()) {
            latestCache.putAll(loaded);
        }
        return loaded;
    }

    private static boolean contains(Viewport viewport, CachedSpot spot) {
        if (spot.lat() == null || spot.lng() == null) {
            return false;
        }
        BigDecimal lat = BigDecimal.valueOf(spot.lat());
        BigDecimal lng = BigDecimal.valueOf(spot.lng());
        return viewport.swLat().compareTo(lat) <= 0 && lat.compareTo(viewport.neLat()) <= 0
                && viewport.swLng().compareTo(lng) <= 0 && lng.compareTo(viewport.neLng()) <= 0;
    }
}
