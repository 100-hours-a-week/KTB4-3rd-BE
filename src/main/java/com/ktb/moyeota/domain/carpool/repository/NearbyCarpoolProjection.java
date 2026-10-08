package com.ktb.moyeota.domain.carpool.repository;

import java.time.LocalDateTime;

public interface NearbyCarpoolProjection {

    Long getId();

    String getHostName();

    String getHostProfileImageUrl();

    String getOriginName();

    String getDestName();

    LocalDateTime getDepartureAt();

    Double getDistanceM();

    Integer getCurrentCount();

    Integer getCapacity();
}
