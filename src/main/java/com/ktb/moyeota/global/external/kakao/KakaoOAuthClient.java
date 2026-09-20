package com.ktb.moyeota.global.external.kakao;

import java.net.URI;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriComponentsBuilder;

@Component
@RequiredArgsConstructor
public class KakaoOAuthClient {

    private static final String RESPONSE_TYPE = "code";

    private final KakaoProperties kakaoProperties;

    public URI buildAuthorizeUri(String state) {
        return UriComponentsBuilder.fromUriString(kakaoProperties.authorizeUri())
                .queryParam("client_id", "{clientId}")
                .queryParam("redirect_uri", "{redirectUri}")
                .queryParam("response_type", "{responseType}")
                .queryParam("state", "{state}")
                .encode()
                .buildAndExpand(
                        kakaoProperties.clientId(),
                        kakaoProperties.redirectUri(),
                        RESPONSE_TYPE,
                        state)
                .toUri();
    }
}
