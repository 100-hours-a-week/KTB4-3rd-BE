package com.ktb.moyeota.domain.community.repository;

import java.time.LocalDateTime;

public interface CommunityNearbyProjection {

    Long getId();

    String getTitle();

    Integer getCommentCount();

    LocalDateTime getCreatedAt();

    String getAuthorNickname();

    String getAuthorProfileImageUrl();

    Double getDistanceM();
}
