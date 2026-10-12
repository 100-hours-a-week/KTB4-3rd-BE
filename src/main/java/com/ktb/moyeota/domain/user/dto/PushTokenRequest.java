package com.ktb.moyeota.domain.user.dto;

public record PushTokenRequest(
        String token
) {

    public static final int MAX_LENGTH = 255;

    public boolean isBlank() {
        return token == null || token.isBlank();
    }

    public boolean exceedsMaxLength() {
        return token != null && token.length() > MAX_LENGTH;
    }
}
