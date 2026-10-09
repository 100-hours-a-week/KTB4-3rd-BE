package com.ktb.moyeota.domain.carpool.controller;

import com.ktb.moyeota.domain.carpool.dto.CarpoolCreateRequest;
import com.ktb.moyeota.domain.carpool.dto.CarpoolCreateResponse;
import com.ktb.moyeota.domain.carpool.dto.CarpoolDetailResponse;
import com.ktb.moyeota.domain.carpool.dto.CarpoolPinSearchRequest;
import com.ktb.moyeota.domain.carpool.dto.CarpoolPinSearchResponse;
import com.ktb.moyeota.domain.carpool.dto.CarpoolStatusChangeRequest;
import com.ktb.moyeota.domain.carpool.entity.CompanionRequestStatus;
import com.ktb.moyeota.domain.carpool.dto.JoinRequestHandleRequest;
import com.ktb.moyeota.domain.carpool.dto.JoinRequestHandleResponse;
import com.ktb.moyeota.domain.carpool.dto.JoinRequestSendRequest;
import com.ktb.moyeota.domain.carpool.dto.JoinRequestSendResponse;
import com.ktb.moyeota.domain.carpool.dto.NearbyCarpoolSearchRequest;
import com.ktb.moyeota.domain.carpool.dto.NearbyCarpoolSearchResponse;
import com.ktb.moyeota.domain.carpool.model.CarpoolPins;
import com.ktb.moyeota.domain.carpool.model.CreatedCarpool;
import com.ktb.moyeota.domain.carpool.model.HandledJoinRequest;
import com.ktb.moyeota.domain.carpool.model.SentJoinRequest;
import com.ktb.moyeota.domain.carpool.service.CarpoolRegistrationService;
import com.ktb.moyeota.domain.carpool.service.CarpoolRequestService;
import com.ktb.moyeota.domain.carpool.service.CarpoolRideService;
import com.ktb.moyeota.domain.carpool.service.CarpoolService;
import com.ktb.moyeota.domain.carpool.success.CarpoolSuccessCode;
import com.ktb.moyeota.global.common.ApiResponse;
import com.ktb.moyeota.global.security.resolver.AuthUser;
import com.ktb.moyeota.global.security.resolver.AuthUserOptional;
import jakarta.validation.Valid;
import java.net.URI;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class CarpoolController {

    private final CarpoolService carpoolService;
    private final CarpoolRideService carpoolRideService;
    private final CarpoolRegistrationService carpoolRegistrationService;
    private final CarpoolRequestService carpoolRequestService;

    @PostMapping("/carpools")
    public ResponseEntity<ApiResponse<CarpoolCreateResponse>> create(
            @AuthUser Long userId, @Valid @RequestBody CarpoolCreateRequest request) {
        CreatedCarpool carpool = carpoolRegistrationService.create(userId, request.toCommand());
        return ResponseEntity.created(URI.create("/api/carpools/" + carpool.id()))
                .body(ApiResponse.of(CarpoolSuccessCode.CARPOOL_CREATED, CarpoolCreateResponse.from(carpool)));
    }

    @GetMapping("/carpool-pins")
    public ApiResponse<CarpoolPinSearchResponse> getPins(@Valid CarpoolPinSearchRequest request) {
        CarpoolPins pins = carpoolService.findPins(request.toViewport());
        return ApiResponse.of(
                pins.limitExceeded() ? CarpoolSuccessCode.TOO_MANY_PINS : CarpoolSuccessCode.PINS_FOUND,
                CarpoolPinSearchResponse.from(pins));
    }

    @GetMapping("/carpools")
    public ApiResponse<NearbyCarpoolSearchResponse> getNearby(@Valid NearbyCarpoolSearchRequest request) {
        return ApiResponse.of(CarpoolSuccessCode.NEARBY_CARPOOLS_FOUND,
                NearbyCarpoolSearchResponse.from(carpoolService.findNearby(request.toQuery())));
    }

    @GetMapping("/carpools/{companion_id}")
    public ApiResponse<CarpoolDetailResponse> getDetail(
            @AuthUserOptional Long userId, @PathVariable("companion_id") Long carpoolId) {
        return ApiResponse.of(CarpoolSuccessCode.DETAIL_FOUND,
                CarpoolDetailResponse.from(carpoolService.findDetail(carpoolId, userId)));
    }

    @PostMapping("/carpools/{companion_id}/join-requests")
    public ResponseEntity<ApiResponse<JoinRequestSendResponse>> sendJoinRequest(
            @AuthUser Long userId,
            @PathVariable("companion_id") Long carpoolId,
            @Valid @RequestBody JoinRequestSendRequest request) {
        SentJoinRequest sent = carpoolRequestService.send(userId, carpoolId, request.content());
        return ResponseEntity.created(URI.create("/api/carpools/" + carpoolId + "/join-requests/" + sent.id()))
                .body(ApiResponse.of(CarpoolSuccessCode.JOIN_REQUEST_SENT, JoinRequestSendResponse.from(sent)));
    }

    @PatchMapping("/carpools/{companion_id}/join-requests/{request_id}")
    public ApiResponse<JoinRequestHandleResponse> handleJoinRequest(
            @AuthUser Long userId,
            @PathVariable("companion_id") Long carpoolId,
            @PathVariable("request_id") Long requestId,
            @Valid @RequestBody JoinRequestHandleRequest request) {
        HandledJoinRequest handled = carpoolRequestService.handle(userId, carpoolId, requestId, request.toStatus());
        return ApiResponse.of(
                handled.status() == CompanionRequestStatus.ACCEPTED
                        ? CarpoolSuccessCode.JOIN_REQUEST_ACCEPTED
                        : CarpoolSuccessCode.JOIN_REQUEST_REJECTED,
                JoinRequestHandleResponse.from(handled));
    }

    @PatchMapping("/carpools/{companion_id}")
    public ApiResponse<CarpoolDetailResponse> changeStatus(
            @AuthUser Long userId,
            @PathVariable("companion_id") Long carpoolId,
            @Valid @RequestBody CarpoolStatusChangeRequest request) {
        return ApiResponse.of(CarpoolSuccessCode.STATUS_CHANGED, CarpoolDetailResponse.from(
                carpoolRideService.changeStatus(userId, carpoolId, request.toStatus())));
    }

    @DeleteMapping("/carpools/{companion_id}/participants/me")
    public ResponseEntity<Void> leave(@AuthUser Long userId, @PathVariable("companion_id") Long carpoolId) {
        carpoolRideService.leave(userId, carpoolId);
        return ResponseEntity.noContent().build();
    }
}
