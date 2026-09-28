package com.ktb.moyeota.domain.auth.model;

import com.ktb.moyeota.global.security.jwt.AccessToken;
import java.time.Duration;

public sealed interface ReissueResult {

    record Rotated(AccessToken accessToken, String refreshToken, Duration refreshTokenMaxAge)
            implements ReissueResult {
    }

    record Graced(AccessToken accessToken) implements ReissueResult {
    }

    record Rejected() implements ReissueResult {
    }
}
