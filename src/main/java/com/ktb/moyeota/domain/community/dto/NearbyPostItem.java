package com.ktb.moyeota.domain.community.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.LocalDateTime;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record NearbyPostItem(
        MapPinType type,
        Long id,
        String title,
        NearbyPostAuthor author,
        Double distanceM,

        // COMMUNITY 전용
        Integer commentCount,
        LocalDateTime createdAt,

        // COMPANION 전용
        Integer currentCount,
        Integer capacity,
        LocalDateTime departureAt,
        Boolean isExpired
) {

    public static NearbyPostItem ofCommunity(
            Long id, String title, NearbyPostAuthor author, double distanceM,
            Integer commentCount, LocalDateTime createdAt) {
        return new NearbyPostItem(
                MapPinType.COMMUNITY, id, title, author, distanceM,
                commentCount, createdAt,
                null, null, null, null);
    }

    public static NearbyPostItem ofCompanion(
            Long id, String title, NearbyPostAuthor author, double distanceM,
            Integer currentCount, Integer capacity, LocalDateTime departureAt, boolean isExpired) {
        return new NearbyPostItem(
                MapPinType.COMPANION, id, title, author, distanceM,
                null, null,
                currentCount, capacity, departureAt, isExpired);
    }
}
