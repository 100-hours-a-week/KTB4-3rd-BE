package com.ktb.moyeota.domain.community.controller;

import com.ktb.moyeota.domain.community.dto.MapPinSearchRequest;
import com.ktb.moyeota.domain.community.dto.MapPinSearchResponse;
import com.ktb.moyeota.domain.community.service.HomeService;
import com.ktb.moyeota.global.common.ApiResponse;
import java.math.BigDecimal;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 네비게이션 바 '홈' — GET /map-pins.
 *
 * [주의할 내용] GET /nearby-posts(바텀시트)는 이번 라운드에서 제외했다.
 * - NearbyPostSearchRequest/Response DTO가 아직 없고
 * - 4번 처리 로직(두 테이블 데이터 조회·정렬 방식)이 테크스펙에서 명시적으로 TODO로 남겨진 부분이라,
 *   Controller 메서드를 먼저 만들면 응답 타입을 임의로 정하게 돼서 보류함.
 *
 * [확인 필요] 쿼리 파라미터는 sw_lat/ne_lat처럼 snake_case인데 MapPinSearchRequest 필드는
 * swLat처럼 camelCase라, @Valid @ModelAttribute로 한 번에 바인딩하면 매핑이 안 된다.
 * 그래서 지금은 @RequestParam으로 필드별로 받아 컨트롤러 안에서 직접 레코드를 조립했는데,
 * 이러면 MapPinSearchRequest에 붙은 @NotNull/@DecimalMin 같은 Bean Validation 애노테이션이
 * 실제로는 전혀 검증되지 않는다(스펙의 "컨트롤러에서 @Valid 로 실행"과 어긋남).
 * 이건 이 API만의 문제가 아니라 snake_case 쿼리 파라미터를 쓰는 GET 전반에 걸친 공통 패턴
 * 문제라 Community 범위를 넘어서는 것 같아서 일단 손대지 않고 그대로 뒀다 — 전역 컨버전/바인딩
 * 설정을 추가할지, 필드 검증을 Service 계층에서 수동으로 할지 정해줘야 할 것 같다.
 */
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
        MapPinSearchResponse response = homeService.search(request);
        return ResponseEntity.ok(ApiResponse.success(null, response));
    }
}
