package com.ktb.moyeota.domain.community.repository;

import java.math.BigDecimal;

/** 지도 핀(map-pins) 조회용 projection. community_posts에서 지도에 필요한 컬럼만 뽑는다. */
public interface CommunityPinProjection {

    Long getId();

    String getTitle();

    BigDecimal getLat();

    BigDecimal getLng();
}
