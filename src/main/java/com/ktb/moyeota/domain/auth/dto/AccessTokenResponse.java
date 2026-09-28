package com.ktb.moyeota.domain.auth.dto;

import com.ktb.moyeota.global.security.jwt.AccessToken;

public record AccessTokenResponse(String accessToken, long expiresIn) {

    public static AccessTokenResponse from(AccessToken accessToken) {
        return new AccessTokenResponse(accessToken.value(), accessToken.expiresIn());
    }
}
