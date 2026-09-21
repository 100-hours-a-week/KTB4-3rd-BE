package com.ktb.moyeota.domain.user.dto;

import com.ktb.moyeota.domain.user.model.AgreementsCommand;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotNull;

public record AgreementsRequest(
        @NotNull
        @AssertTrue
        Boolean service,

        @NotNull
        @AssertTrue
        Boolean location,

        @NotNull
        @AssertTrue
        Boolean gender,

        @NotNull
        Boolean accountThirdParty,

        @NotNull
        Boolean marketing) {

    public AgreementsCommand toCommand() {
        return new AgreementsCommand(accountThirdParty, marketing);
    }
}
