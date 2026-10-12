package com.ktb.moyeota.domain.settlement.controller;

import com.ktb.moyeota.domain.settlement.dto.SettlementAmountRequest;
import com.ktb.moyeota.domain.settlement.dto.SettlementAmountResponse;
import com.ktb.moyeota.domain.settlement.model.RequestedSettlement;
import com.ktb.moyeota.domain.settlement.service.SettlementService;
import com.ktb.moyeota.domain.settlement.success.SettlementSuccessCode;
import com.ktb.moyeota.global.common.ApiResponse;
import com.ktb.moyeota.global.security.resolver.AuthUser;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class SettlementController {

    private final SettlementService settlementService;

    @PutMapping("/settlements/{settlement_id}/amount")
    public ApiResponse<SettlementAmountResponse> inputAmount(
            @AuthUser Long userId,
            @PathVariable("settlement_id") Long settlementId,
            @Valid @RequestBody SettlementAmountRequest request) {
        RequestedSettlement requested = settlementService.requestManually(userId, settlementId, request.totalAmountValue());
        return ApiResponse.of(SettlementSuccessCode.AMOUNT_ENTERED, toResponse(requested));
    }

    private static SettlementAmountResponse toResponse(RequestedSettlement requested) {
        return new SettlementAmountResponse(
                requested.participantCount(),
                requested.amountPerPerson(),
                requested.status().name(),
                requested.meterAnalysisStatus().name());
    }
}
