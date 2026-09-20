package com.ktb.moyeota.domain.auth.model;

import java.util.Arrays;
import java.util.Optional;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum OAuthProvider {

    KAKAO("kakao");

    private final String pathValue;

    public static Optional<OAuthProvider> from(String pathValue) {
        return Arrays.stream(values())
                .filter(provider -> provider.pathValue.equals(pathValue))
                .findFirst();
    }
}
