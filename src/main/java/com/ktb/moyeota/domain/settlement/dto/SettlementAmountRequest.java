package com.ktb.moyeota.domain.settlement.dto;

import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;

public record SettlementAmountRequest(
        @NotNull
        @Positive
        @Digits(integer = 6, fraction = 0)
        BigDecimal totalAmount) {

    public int totalAmountValue() {
        return totalAmount.intValueExact();
    }
}
