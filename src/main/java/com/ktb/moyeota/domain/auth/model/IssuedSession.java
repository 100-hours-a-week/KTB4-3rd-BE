package com.ktb.moyeota.domain.auth.model;

import com.ktb.moyeota.global.security.jwt.AccessToken;
import java.time.Duration;

public record IssuedSession(AccessToken accessToken, String refreshToken, Duration refreshTokenMaxAge) {
}
