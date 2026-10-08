package com.ktb.moyeota.domain.carpool.controller;

import com.ktb.moyeota.domain.carpool.dto.CarpoolPinSearchRequest;
import com.ktb.moyeota.domain.carpool.dto.CarpoolPinSearchResponse;
import com.ktb.moyeota.domain.carpool.model.CarpoolPins;
import com.ktb.moyeota.domain.carpool.service.CarpoolService;
import com.ktb.moyeota.domain.carpool.success.CarpoolSuccessCode;
import com.ktb.moyeota.global.common.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class CarpoolController {

    private final CarpoolService carpoolService;

    @GetMapping("/carpool-pins")
    public ApiResponse<CarpoolPinSearchResponse> getPins(@Valid CarpoolPinSearchRequest request) {
        CarpoolPins pins = carpoolService.findPins(request.toViewport());
        return ApiResponse.of(
                pins.limitExceeded() ? CarpoolSuccessCode.TOO_MANY_PINS : CarpoolSuccessCode.PINS_FOUND,
                CarpoolPinSearchResponse.from(pins));
    }
}
