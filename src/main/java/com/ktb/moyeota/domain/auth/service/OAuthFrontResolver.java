package com.ktb.moyeota.domain.auth.service;

import com.ktb.moyeota.domain.auth.model.OAuthFront;
import com.ktb.moyeota.domain.auth.model.OAuthProvider;
import com.ktb.moyeota.global.config.OAuthProperties;
import com.ktb.moyeota.global.external.kakao.KakaoProperties;
import java.net.URI;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class OAuthFrontResolver {

    private final OAuthProperties oAuthProperties;
    private final KakaoProperties kakaoProperties;

    public OAuthFront resolve(OAuthProvider provider, String requestedOrigin) {
        String redirectUri = defaultRedirectUri(provider);
        String callbackUri = oAuthProperties.frontCallbackUri();
        if (requestedOrigin == null || !oAuthProperties.extraFrontOrigins().contains(requestedOrigin)) {
            return new OAuthFront(null, redirectUri, callbackUri);
        }
        return new OAuthFront(
                requestedOrigin,
                requestedOrigin + pathOf(redirectUri),
                requestedOrigin + pathOf(callbackUri));
    }

    private String defaultRedirectUri(OAuthProvider provider) {
        return switch (provider) {
            case KAKAO -> kakaoProperties.redirectUri();
        };
    }

    private String pathOf(String uri) {
        return URI.create(uri).getRawPath();
    }
}
