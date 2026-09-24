package com.ktb.moyeota.domain.auth.model;

public record OAuthFront(String origin, String redirectUri, String callbackUri) {

    public boolean isDefault() {
        return origin == null;
    }
}
