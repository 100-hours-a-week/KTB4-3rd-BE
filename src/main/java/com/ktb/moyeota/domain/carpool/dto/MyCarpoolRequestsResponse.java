package com.ktb.moyeota.domain.carpool.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.ktb.moyeota.domain.carpool.model.MyCarpoolRequestItem;
import com.ktb.moyeota.domain.carpool.model.MyCarpoolRequests;
import java.time.LocalDateTime;
import java.util.List;

public record MyCarpoolRequestsResponse(String direction, List<Item> items, String nextCursor) {

    public static MyCarpoolRequestsResponse from(MyCarpoolRequests requests) {
        return new MyCarpoolRequestsResponse(
                requests.direction().name(),
                requests.items().stream().map(Item::from).toList(),
                requests.nextCursor());
    }

    public record Item(
            Long id,
            Long carpoolId,
            String status,
            String content,
            Counterpart counterpart,
            String originName,
            String destName,
            LocalDateTime departureAt,
            @JsonInclude(JsonInclude.Include.NON_NULL) Long chatRoomId,
            LocalDateTime createdAt) {

        static Item from(MyCarpoolRequestItem item) {
            return new Item(
                    item.id(),
                    item.carpoolId(),
                    item.status().name(),
                    item.content(),
                    new Counterpart(item.counterpart().id(), item.counterpart().name(), item.counterpart().profileImageUrl()),
                    item.originName(),
                    item.destName(),
                    item.departureAt(),
                    item.chatRoomId(),
                    item.createdAt());
        }
    }

    public record Counterpart(Long id, String name, String profileImageUrl) {
    }
}
