package com.ktb.moyeota.domain.user.controller;

import com.ktb.moyeota.domain.user.dto.CarRegisterResponse;
import com.ktb.moyeota.domain.user.dto.CarUpdateResponse;
import com.ktb.moyeota.domain.user.dto.MyCarsResponse;
import com.ktb.moyeota.domain.user.dto.OwnedCarRequest;
import com.ktb.moyeota.domain.user.model.MyCar;
import com.ktb.moyeota.domain.user.service.OwnedCarService;
import com.ktb.moyeota.domain.user.success.UserSuccessCode;
import com.ktb.moyeota.global.common.ApiResponse;
import com.ktb.moyeota.global.security.resolver.AuthUser;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/users/me/cars")
@RequiredArgsConstructor
public class OwnedCarController {

    private final OwnedCarService ownedCarService;

    @PostMapping
    public ResponseEntity<ApiResponse<CarRegisterResponse>> register(
            @AuthUser Long userId, @Valid @RequestBody OwnedCarRequest request) {
        MyCar car = ownedCarService.register(userId, request.toCommand());
        return ResponseEntity.created(URI.create("/api/users/me/cars/" + car.id()))
                .body(ApiResponse.of(UserSuccessCode.CAR_REGISTERED, CarRegisterResponse.from(car)));
    }

    @PutMapping("/{car_id}")
    public ApiResponse<CarUpdateResponse> update(
            @AuthUser Long userId, @PathVariable("car_id") Long carId, @Valid @RequestBody OwnedCarRequest request) {
        return ApiResponse.of(UserSuccessCode.CAR_UPDATED,
                CarUpdateResponse.from(ownedCarService.update(userId, carId, request.toCommand())));
    }

    @DeleteMapping("/{car_id}")
    public ResponseEntity<Void> delete(@AuthUser Long userId, @PathVariable("car_id") Long carId) {
        ownedCarService.delete(userId, carId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping
    public ApiResponse<MyCarsResponse> getAll(@AuthUser Long userId) {
        List<MyCar> cars = ownedCarService.findAll(userId);
        return ApiResponse.of(cars.isEmpty() ? UserSuccessCode.NO_CAR : UserSuccessCode.CARS_FOUND,
                MyCarsResponse.from(cars));
    }
}
