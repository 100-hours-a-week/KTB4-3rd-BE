package com.ktb.moyeota.domain.user.dto;

import com.ktb.moyeota.domain.user.model.MyCar;
import java.time.LocalDateTime;

public record CarUpdateResponse(LocalDateTime updatedAt) {

    public static CarUpdateResponse from(MyCar car) {
        return new CarUpdateResponse(car.updatedAt());
    }
}
