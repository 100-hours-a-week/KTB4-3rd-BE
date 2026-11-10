package com.ktb.moyeota.domain.carpool.repository;

import java.math.BigDecimal;

public interface CarpoolPinProjection {

    Long getId();

    BigDecimal getOriginLat();

    BigDecimal getOriginLng();
}
