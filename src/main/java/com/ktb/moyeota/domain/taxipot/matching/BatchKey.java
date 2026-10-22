package com.ktb.moyeota.domain.taxipot.matching;

import java.time.LocalDateTime;

public record BatchKey(RegionCell region, LocalDateTime departureAt) {
}
