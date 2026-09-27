package com.ktb.moyeota.domain.rating.controller;

import com.ktb.moyeota.domain.rating.dto.RatingSubmitRequest;
import com.ktb.moyeota.domain.rating.model.RatingSubmitCommand;
import com.ktb.moyeota.domain.rating.service.RatingService;
import com.ktb.moyeota.domain.rating.success.RatingSuccessCode;
import com.ktb.moyeota.global.common.ApiResponse;
import com.ktb.moyeota.global.security.resolver.AuthUser;
import jakarta.validation.Valid;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/companions/{companion_id}/ratings")
@RequiredArgsConstructor
public class RatingController {

    private final RatingService ratingService;

    @PostMapping
    public ResponseEntity<ApiResponse<Optional<Void>>> submit(
            @AuthUser Long userId,
            @PathVariable("companion_id") Long companionId,
            @Valid @RequestBody RatingSubmitRequest request) {
        ratingService.submit(userId, companionId, toCommand(request));
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.of(RatingSuccessCode.RATING_SUBMITTED, Optional.empty()));
    }

    private static RatingSubmitCommand toCommand(RatingSubmitRequest request) {
        return new RatingSubmitCommand(request.ratings().stream()
                .map(rating -> new RatingSubmitCommand.Rating(rating.targetUserId(), rating.score()))
                .toList());
    }
}
