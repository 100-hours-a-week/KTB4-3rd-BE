package com.ktb.moyeota.domain.user.dto;

import com.ktb.moyeota.domain.user.model.MyCar;
import java.util.List;

public record MyCarsResponse(List<Item> cars) {

    public static MyCarsResponse from(List<MyCar> cars) {
        return new MyCarsResponse(cars.stream().map(Item::from).toList());
    }

    public record Item(Long id, String model, String number) {

        static Item from(MyCar car) {
            return new Item(car.id(), car.model(), car.number());
        }
    }
}
