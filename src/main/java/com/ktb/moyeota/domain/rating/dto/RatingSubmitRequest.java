package com.ktb.moyeota.domain.rating.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.util.List;

public record RatingSubmitRequest(
        @NotEmpty
        List<@Valid @NotNull Rating> ratings) {

    public record Rating(
            @NotNull
            Long targetUserId,

            @NotNull
            @Min(1)
            @Max(5)
            Integer score) {
    }
}
