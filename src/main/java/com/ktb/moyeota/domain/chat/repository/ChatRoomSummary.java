package com.ktb.moyeota.domain.chat.repository;

import java.time.LocalDateTime;

public interface ChatRoomSummary {

    Long getId();

    Long getCompanionId();

    String getKind();

    String getOriginName();

    LocalDateTime getDepartureAt();

    Integer getCurrentCount();

    Integer getCapacity();

    String getHostProfileImageUrl();
}
