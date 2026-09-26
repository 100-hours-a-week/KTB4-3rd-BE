package com.ktb.moyeota.domain.taxipot.controller;

import com.ktb.moyeota.domain.companion.entity.CompanionStatus;
import com.ktb.moyeota.domain.taxipot.dto.CurrentTaxiPotResponse;
import com.ktb.moyeota.domain.taxipot.dto.TaxiPotDetailResponse;
import com.ktb.moyeota.domain.taxipot.dto.TaxiPotStartRequest;
import com.ktb.moyeota.domain.taxipot.dto.TaxiPotStatusChangeRequest;
import com.ktb.moyeota.domain.taxipot.model.CurrentTaxiPot;
import com.ktb.moyeota.domain.taxipot.model.TaxiPotDetail;
import com.ktb.moyeota.domain.taxipot.model.TaxiPotStartCommand;
import com.ktb.moyeota.domain.taxipot.service.TaxiPotService;
import com.ktb.moyeota.domain.taxipot.success.TaxiPotSuccessCode;
import com.ktb.moyeota.global.common.ApiResponse;
import com.ktb.moyeota.global.security.resolver.AuthUser;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
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
                        ? TaxiPotSuccessCode.TAXI_POT_FOUND
                        : TaxiPotSuccessCode.NO_CURRENT_TAXI_POT,
                current);
    }

    @PostMapping("/taxi-pots")
    public ResponseEntity<ApiResponse<CurrentTaxiPotResponse>> start(
            @AuthUser Long userId, @Valid @RequestBody TaxiPotStartRequest request) {
        CurrentTaxiPot taxiPot = taxiPotService.start(userId, toCommand(request));
        return ResponseEntity.created(URI.create("/api/taxi-pots/" + taxiPot.id()))
                .body(ApiResponse.of(TaxiPotSuccessCode.MATCH_STARTED, toResponse(taxiPot)));
    }

    @DeleteMapping("/taxi-pots/{companion_id}/participants/me")
    public ResponseEntity<Void> leave(@AuthUser Long userId, @PathVariable("companion_id") Long taxiPotId) {
        taxiPotService.leave(userId, taxiPotId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/taxi-pots/{companion_id}")
    public ApiResponse<TaxiPotDetailResponse> get(
            @AuthUser Long userId, @PathVariable("companion_id") Long taxiPotId) {
        TaxiPotDetail detail = taxiPotService.find(userId, taxiPotId);
        return ApiResponse.of(TaxiPotSuccessCode.TAXI_POT_FOUND, toResponse(detail));
    }

    @PatchMapping("/taxi-pots/{companion_id}")
    public ApiResponse<TaxiPotDetailResponse> changeStatus(
            @AuthUser Long userId,
            @PathVariable("companion_id") Long taxiPotId,
            @Valid @RequestBody TaxiPotStatusChangeRequest request) {
        CompanionStatus target = CompanionStatus.valueOf(request.status());
        TaxiPotDetail detail = taxiPotService.changeStatus(userId, taxiPotId, target);
        return ApiResponse.of(TaxiPotSuccessCode.STATUS_CHANGED, toResponse(detail));
    }

    private static TaxiPotStartCommand toCommand(TaxiPotStartRequest request) {
        return new TaxiPotStartCommand(
                request.originName(),
                request.originLat(),
                request.originLng(),
                request.destName(),
                request.destLat(),
                request.destLng(),
                request.departureAt());
    }

    private static CurrentTaxiPotResponse toResponse(CurrentTaxiPot taxiPot) {
        return new CurrentTaxiPotResponse(
                taxiPot.id(), null, taxiPot.status().name(), taxiPot.currentCount(), taxiPot.capacity());
    }

    private static TaxiPotDetailResponse toResponse(TaxiPotDetail detail) {
        return new TaxiPotDetailResponse(
                detail.id(),
                null,
                detail.status().name(),
                detail.originName(),
                detail.destName(),
                detail.departureAt(),
                detail.currentCount(),
                detail.capacity(),
                detail.hostId());
    }
}
