package com.ktb.moyeota.domain.user.dto;

import com.ktb.moyeota.domain.user.model.MyCar;
import java.time.LocalDateTime;

public record CarRegisterResponse(Long id, LocalDateTime createdAt) {

    public static CarRegisterResponse from(MyCar car) {
        return new CarRegisterResponse(car.id(), car.createdAt());
    }
}
