package com.ktb.moyeota.domain.user.model;

import com.ktb.moyeota.domain.user.entity.OwnedCar;
import java.time.LocalDateTime;

public record MyCar(Long id, String model, String number, LocalDateTime createdAt, LocalDateTime updatedAt) {

    public static MyCar from(OwnedCar car) {
        return new MyCar(car.getId(), car.getModel(), car.getNumber(), car.getCreatedAt(), car.getUpdatedAt());
    }
}
