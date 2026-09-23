package com.ktb.moyeota.domain.community.repository;

import java.math.BigDecimal;

public interface CommunityPinProjection {

    Long getId();

    String getTitle();

    BigDecimal getLat();

    BigDecimal getLng();
}
