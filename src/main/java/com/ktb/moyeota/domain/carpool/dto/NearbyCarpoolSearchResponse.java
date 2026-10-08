package com.ktb.moyeota.domain.carpool.dto;

import com.ktb.moyeota.domain.carpool.model.NearbyCarpool;
import com.ktb.moyeota.domain.carpool.model.NearbyCarpools;
import java.time.LocalDateTime;
import java.util.List;

public record NearbyCarpoolSearchResponse(List<Item> items, String nextCursor) {

    public static NearbyCarpoolSearchResponse from(NearbyCarpools carpools) {
        return new NearbyCarpoolSearchResponse(
                carpools.carpools().stream().map(Item::from).toList(), carpools.nextCursor());
    }

    public record Item(
            Long id,
            Host host,
            String originName,
            String destName,
            LocalDateTime departureAt,
            double distanceM,
            int currentCount,
            int capacity,
            Boolean isFull,
            Boolean isExpired) {

        static Item from(NearbyCarpool carpool) {
            return new Item(
                    carpool.id(),
                    new Host(carpool.hostNickname(), carpool.hostProfileImageUrl()),
                    carpool.originName(),
                    carpool.destName(),
                    carpool.departureAt(),
                    carpool.distanceM(),
                    carpool.currentCount(),
                    carpool.capacity(),
                    carpool.full(),
                    carpool.expired());
        }
    }

    public record Host(String nickname, String profileImageUrl) {
    }
}
