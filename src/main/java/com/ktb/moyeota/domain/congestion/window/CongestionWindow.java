package com.ktb.moyeota.domain.congestion.window;

import java.time.OffsetDateTime;

public record CongestionWindow(OffsetDateTime start, OffsetDateTime end) {
}
