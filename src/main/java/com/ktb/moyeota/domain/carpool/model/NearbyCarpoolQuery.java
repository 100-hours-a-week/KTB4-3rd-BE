package com.ktb.moyeota.domain.carpool.model;

import com.ktb.moyeota.global.common.Viewport;
import java.math.BigDecimal;

public record NearbyCarpoolQuery(BigDecimal lat, BigDecimal lng, Viewport viewport, String cursor) {
}
