package com.ktb.moyeota.domain.congestion.dto;

import com.ktb.moyeota.domain.congestion.cache.CachedSpot;
import com.ktb.moyeota.domain.congestion.document.SpotType;
import com.ktb.moyeota.domain.congestion.status.CongestionStatus;
import java.time.LocalDateTime;
import java.time.ZoneId;

public record CongestionSpotItem(
        Long id,
        SpotType type,
        String name,
        Double lat,
        Double lng,
        CongestionStatus status,
        LocalDateTime windowEnd) {

    private static final ZoneId RESPONSE_ZONE = ZoneId.of("Asia/Seoul");

    public static CongestionSpotItem from(CachedSpot spot) {
        LocalDateTime windowEnd = spot.resultWindowEnd() == null
                ? null
                : LocalDateTime.ofInstant(spot.resultWindowEnd(), RESPONSE_ZONE);
        return new CongestionSpotItem(spot.id(), spot.type(), spot.name(), spot.lat(), spot.lng(), spot.status(),
                windowEnd);
    }
}
