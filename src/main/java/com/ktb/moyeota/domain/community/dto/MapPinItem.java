package com.ktb.moyeota.domain.community.dto;

import java.math.BigDecimal;

public record MapPinItem(
        MapPinType type,
        Long id,
        BigDecimal lat,
        BigDecimal lng
) {
}
