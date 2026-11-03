package com.ktb.moyeota.domain.congestion.cache;

import com.ktb.moyeota.domain.congestion.document.CauseReport;
import com.ktb.moyeota.domain.congestion.document.Spot;
import com.ktb.moyeota.domain.congestion.document.SpotType;
import com.ktb.moyeota.domain.congestion.model.CongestionLevel;
import com.ktb.moyeota.domain.congestion.status.CongestionStatus;
import java.time.Instant;
import java.util.List;
import org.springframework.data.mongodb.core.geo.GeoJsonPoint;

public record CachedSpot(
        Long id,
        SpotType type,
        String name,
        Double lat,
        Double lng,
        CongestionStatus status,
        CongestionLevel congestionLevel,
        List<CauseReport> causes,
        String summary,
        Integer reportCount,
        Instant calculatedAt,
        Instant validUntil) {

    public CachedSpot {
        causes = causes == null ? List.of() : List.copyOf(causes);
    }

    public static CachedSpot from(Spot spot) {
        GeoJsonPoint location = spot.getLocation();
        return new CachedSpot(
                spot.getId(),
                spot.getType(),
                spot.getName(),
                location == null ? null : location.getY(),
                location == null ? null : location.getX(),
                spot.getStatus(),
                spot.getCongestionLevel(),
                spot.getCauses(),
                spot.getSummary(),
                spot.getReportCount(),
                spot.getCalculatedAt(),
                spot.getValidUntil());
    }
}
