package com.ktb.moyeota.domain.congestion.dto;

import com.ktb.moyeota.domain.congestion.document.SpotType;
import com.ktb.moyeota.domain.congestion.service.CongestionSpotDetail;
import com.ktb.moyeota.domain.congestion.status.CongestionStatus;
import java.time.LocalDateTime;
import java.time.ZoneId;

public record CongestionSpotDetailResponse(
        Long id,
        SpotType type,
        String name,
        Double lat,
        Double lng,
        CongestionStatus status,
        LocalDateTime windowEnd,
        String reason,
        boolean analyzing) {

    private static final ZoneId RESPONSE_ZONE = ZoneId.of("Asia/Seoul");

    public static CongestionSpotDetailResponse from(CongestionSpotDetail detail) {
        return new CongestionSpotDetailResponse(
                detail.id(),
                detail.type(),
                detail.name(),
                detail.lat(),
                detail.lng(),
                detail.status(),
                detail.windowEnd() == null ? null : LocalDateTime.ofInstant(detail.windowEnd(), RESPONSE_ZONE),
                detail.reason(),
                detail.analyzing());
    }
}
