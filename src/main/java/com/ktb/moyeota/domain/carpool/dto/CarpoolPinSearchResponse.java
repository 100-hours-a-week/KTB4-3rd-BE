package com.ktb.moyeota.domain.carpool.dto;

import com.ktb.moyeota.domain.carpool.model.CarpoolPin;
import com.ktb.moyeota.domain.carpool.model.CarpoolPins;
import java.math.BigDecimal;
import java.util.List;

public record CarpoolPinSearchResponse(List<Item> items, int limit, boolean limitExceeded) {

    public static CarpoolPinSearchResponse from(CarpoolPins pins) {
        return new CarpoolPinSearchResponse(
                pins.pins().stream().map(Item::from).toList(), pins.limit(), pins.limitExceeded());
    }

    public record Item(Long id, BigDecimal lat, BigDecimal lng) {

        static Item from(CarpoolPin pin) {
            return new Item(pin.id(), pin.lat(), pin.lng());
        }
    }
}
