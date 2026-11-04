package com.ktb.moyeota.domain.congestion.service;

import com.ktb.moyeota.domain.congestion.document.SpotType;
import com.ktb.moyeota.domain.congestion.status.CongestionStatus;
import java.time.Instant;

public record CongestionSpotDetail(
        Long id,
        SpotType type,
        String name,
        Double lat,
        Double lng,
        CongestionStatus status,
        Instant windowEnd,
        String reason,
        boolean analyzing) {
}
