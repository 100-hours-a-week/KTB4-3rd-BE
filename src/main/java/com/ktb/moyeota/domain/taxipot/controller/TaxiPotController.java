package com.ktb.moyeota.domain.taxipot.controller;

import com.ktb.moyeota.domain.taxipot.dto.CurrentTaxiPotResponse;
import com.ktb.moyeota.domain.taxipot.model.CurrentTaxiPot;
import com.ktb.moyeota.domain.taxipot.service.TaxiPotService;
import com.ktb.moyeota.domain.taxipot.success.TaxiPotSuccessCode;
import com.ktb.moyeota.global.common.ApiResponse;
import com.ktb.moyeota.global.security.resolver.AuthUser;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class TaxiPotController {

    private final TaxiPotService taxiPotService;

    @GetMapping("/users/me/current-taxi-pot")
    public ApiResponse<Optional<CurrentTaxiPotResponse>> getMyCurrent(@AuthUser Long userId) {
        Optional<CurrentTaxiPotResponse> current =
                taxiPotService.findMyCurrent(userId).map(TaxiPotController::toResponse);
        return ApiResponse.of(
                current.isPresent()
                        ? TaxiPotSuccessCode.CURRENT_TAXI_POT_FOUND
                        : TaxiPotSuccessCode.NO_CURRENT_TAXI_POT,
                current);
    }

    private static CurrentTaxiPotResponse toResponse(CurrentTaxiPot taxiPot) {
        return new CurrentTaxiPotResponse(
                taxiPot.id(), null, taxiPot.status().name(), taxiPot.currentCount(), taxiPot.capacity());
    }
}
