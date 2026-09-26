package com.ktb.moyeota.domain.chat.repository;

import java.time.LocalDateTime;

public interface ChatRoomListProjection {

    Long getId();

    Long getCompanionId();

    String getKind();

    String getOriginName();

    LocalDateTime getDepartureAt();

    Integer getCurrentCount();

    Integer getCapacity();

    String getHostProfileImageUrl();

    Long getLastMessageId();

    Long getLastReadMessageId();

    LocalDateTime getLastMessageAt();
}
