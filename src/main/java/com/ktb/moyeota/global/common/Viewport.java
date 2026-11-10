package com.ktb.moyeota.global.common;

import com.ktb.moyeota.global.exception.BusinessException;
import com.ktb.moyeota.global.exception.CommonErrorCode;
import java.math.BigDecimal;

public record Viewport(BigDecimal swLat, BigDecimal swLng, BigDecimal neLat, BigDecimal neLng) {

    private static final BigDecimal MAX_SPAN_DEGREES = BigDecimal.ONE;

    public Viewport {
        if (swLat.compareTo(neLat) >= 0 || swLng.compareTo(neLng) >= 0) {
            throw new BusinessException(CommonErrorCode.VIEWPORT_OUT_OF_RANGE);
        }
        if (neLat.subtract(swLat).compareTo(MAX_SPAN_DEGREES) > 0
                || neLng.subtract(swLng).compareTo(MAX_SPAN_DEGREES) > 0) {
            throw new BusinessException(CommonErrorCode.VIEWPORT_TOO_LARGE);
        }
    }
}
