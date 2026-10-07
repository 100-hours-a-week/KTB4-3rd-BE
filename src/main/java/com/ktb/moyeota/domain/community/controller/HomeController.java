package com.ktb.moyeota.domain.community.controller;

import com.ktb.moyeota.domain.community.dto.MapPinSearchRequest;
import com.ktb.moyeota.domain.community.dto.MapPinSearchResponse;
import com.ktb.moyeota.domain.community.dto.NearbyPostSearchRequest;
import com.ktb.moyeota.domain.community.dto.NearbyPostSearchResponse;
import com.ktb.moyeota.domain.community.service.HomeService;
import com.ktb.moyeota.global.common.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class HomeController {

    private final HomeService homeService;

    @GetMapping("/map-pins")
    public ResponseEntity<ApiResponse<MapPinSearchResponse>> getMapPins(@Valid MapPinSearchRequest request) {
        MapPinSearchResponse response = homeService.searchMapPins(request);
        return ResponseEntity.ok(ApiResponse.success("조회에 성공했습니다", response));
    }

    @GetMapping("/nearby-posts")
    public ResponseEntity<ApiResponse<NearbyPostSearchResponse>> getNearbyPosts(
            @Valid NearbyPostSearchRequest request) {
        NearbyPostSearchResponse response = homeService.searchNearbyPosts(request);
        return ResponseEntity.ok(ApiResponse.success(null, response));
    }
}
