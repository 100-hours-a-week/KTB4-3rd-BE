package com.ktb.moyeota.domain.user.dto;

import com.ktb.moyeota.domain.user.model.OwnedCarCommand;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record OwnedCarRequest(
        @NotBlank
        @Size(max = 50)
        String model,

        @NotBlank
        @Size(max = 20)
        String number) {

    public OwnedCarCommand toCommand() {
        return new OwnedCarCommand(model, number);
    }
}
