package com.ktb.moyeota.domain.congestion.controller;

import com.ktb.moyeota.domain.congestion.dto.CongestionSpotDetailResponse;
import com.ktb.moyeota.domain.congestion.dto.CongestionSpotSearchRequest;
import com.ktb.moyeota.domain.congestion.dto.CongestionSpotSearchResponse;
import com.ktb.moyeota.domain.congestion.service.CongestionQueryService;
import com.ktb.moyeota.domain.congestion.service.CongestionSpotDetail;
import com.ktb.moyeota.domain.congestion.success.CongestionSuccessCode;
import com.ktb.moyeota.global.common.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class CongestionController {

    private final CongestionQueryService queryService;

    @GetMapping("/congestion/spots")
    public ResponseEntity<ApiResponse<CongestionSpotSearchResponse>> getSpots(
            @Valid CongestionSpotSearchRequest request) {
        CongestionSpotSearchResponse response = queryService.findSpotsInBounds(request);
        return ResponseEntity.ok(ApiResponse.success("조회에 성공했습니다", response));
    }

    @GetMapping("/congestion/spots/{spot_id}")
    public ApiResponse<CongestionSpotDetailResponse> getSpot(@PathVariable("spot_id") Long spotId) {
        CongestionSpotDetail detail = queryService.findSpot(spotId);
        return ApiResponse.of(successCodeOf(detail), CongestionSpotDetailResponse.from(detail));
    }

    private static CongestionSuccessCode successCodeOf(CongestionSpotDetail detail) {
        if (detail.analyzing()) {
            return CongestionSuccessCode.SPOT_ANALYZING;
        }
        if (detail.reason() == null) {
            return CongestionSuccessCode.SPOT_REASON_MISSING;
        }
        return CongestionSuccessCode.SPOT_FOUND;
    }
}
