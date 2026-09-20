package com.ktb.moyeota.domain.companionpost.repository;

import java.math.BigDecimal;

public interface CompanionPinProjection {

    Long getId();

    String getOriginName();

    String getDestName();

    BigDecimal getOriginLat();

    BigDecimal getOriginLng();
}
