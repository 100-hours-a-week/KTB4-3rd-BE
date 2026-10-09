package com.ktb.moyeota.domain.carpool.dto;

import com.ktb.moyeota.domain.carpool.model.RequestDirection;
import jakarta.validation.constraints.Pattern;

public record MyCarpoolRequestSearchRequest(
        @Pattern(regexp = "^(SENT|RECEIVED)$", message = "INVALID_ENUM")
        String direction,

        String cursor) {

    public RequestDirection toDirection() {
        return direction == null ? RequestDirection.SENT : RequestDirection.valueOf(direction);
    }
}
