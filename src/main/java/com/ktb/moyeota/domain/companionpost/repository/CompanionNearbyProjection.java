package com.ktb.moyeota.domain.companionpost.repository;

import java.time.LocalDateTime;

public interface CompanionNearbyProjection {

    Long getId();

    String getOriginName();

    String getTransportType();

    String getDestName();

    Integer getCurrentCount();

    Integer getCapacity();

    LocalDateTime getDepartureAt();

    LocalDateTime getCreatedAt();

    String getStatus();

    String getHostNickname();

    String getHostProfileImageUrl();

    Double getDistanceM();
}
