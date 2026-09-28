package com.ktb.moyeota.domain.user.dto;

import com.ktb.moyeota.domain.user.model.RegisteredUser;
import java.time.LocalDateTime;

public record SignupResponse(
        Long userId, String accessToken, String profileImageUrl, LocalDateTime createdAt) {

    public static SignupResponse from(RegisteredUser registered) {
        return new SignupResponse(
                registered.userId(),
                registered.session().accessToken().value(),
                registered.profileImageUrl(),
                registered.createdAt());
    }
}
