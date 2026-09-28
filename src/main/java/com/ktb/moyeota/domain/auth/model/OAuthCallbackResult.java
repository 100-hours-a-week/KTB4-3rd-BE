package com.ktb.moyeota.domain.auth.model;

import com.ktb.moyeota.domain.auth.error.OAuthLoginError;
import java.time.Duration;

public sealed interface OAuthCallbackResult {

    record Existing(String refreshToken, Duration refreshTokenMaxAge) implements OAuthCallbackResult {
    }

    record SignupRequired(String signupToken, Duration signupTokenMaxAge) implements OAuthCallbackResult {
    }

    record Failed(OAuthLoginError error) implements OAuthCallbackResult {
    }
}
