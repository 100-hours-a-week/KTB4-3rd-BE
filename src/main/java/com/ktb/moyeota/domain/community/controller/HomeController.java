package com.ktb.moyeota.domain.community.controller;

import com.ktb.moyeota.domain.community.dto.MapPinSearchRequest;
import com.ktb.moyeota.domain.community.dto.MapPinSearchResponse;
import com.ktb.moyeota.domain.community.dto.NearbyPostSearchRequest;
import com.ktb.moyeota.domain.community.dto.NearbyPostSearchResponse;
import com.ktb.moyeota.domain.community.service.HomeService;
import com.ktb.moyeota.global.common.ApiResponse;
import java.math.BigDecimal;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class HomeController {

    private final HomeService homeService;

    @GetMapping("/map-pins")
    public ResponseEntity<ApiResponse<MapPinSearchResponse>> getMapPins(
            @RequestParam("sw_lat") BigDecimal swLat,
            @RequestParam("sw_lng") BigDecimal swLng,
            @RequestParam("ne_lat") BigDecimal neLat,
            @RequestParam("ne_lng") BigDecimal neLng
    ) {
        MapPinSearchRequest request = new MapPinSearchRequest(swLat, swLng, neLat, neLng);
        MapPinSearchResponse response = homeService.searchMapPins(request);
        return ResponseEntity.ok(ApiResponse.success("조회에 성공했습니다", response));
    }

    @GetMapping("/nearby-posts")
    public ResponseEntity<ApiResponse<NearbyPostSearchResponse>> getNearbyPosts(
            @RequestParam("lat") BigDecimal lat,
            @RequestParam("lng") BigDecimal lng,
            @RequestParam("sw_lat") BigDecimal swLat,
            @RequestParam("sw_lng") BigDecimal swLng,
            @RequestParam("ne_lat") BigDecimal neLat,
            @RequestParam("ne_lng") BigDecimal neLng,
            @RequestParam(value = "cursor", required = false) String cursor
    ) {
        NearbyPostSearchRequest request =
                new NearbyPostSearchRequest(lat, lng, swLat, swLng, neLat, neLng, cursor);
        NearbyPostSearchResponse response = homeService.searchNearbyPosts(request);
        return ResponseEntity.ok(ApiResponse.success(null, response));
    }
}
