package com.ktb.moyeota.domain.auth.service;

import com.ktb.moyeota.domain.auth.model.AuthorizeRedirect;
import com.ktb.moyeota.domain.auth.model.OAuthProvider;
import com.ktb.moyeota.global.external.kakao.KakaoOAuthClient;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class OAuthLoginService {

    private static final String ACCESS_DENIED = "access_denied";

    private final KakaoOAuthClient kakaoOAuthClient;
    private final OpaqueTokenFactory opaqueTokenFactory;

    public AuthorizeRedirect buildAuthorizeRedirect(OAuthProvider provider) {
        String state = opaqueTokenFactory.generate();
        URI location = switch (provider) {
            case KAKAO -> kakaoOAuthClient.buildAuthorizeUri(state);
        };
        return new AuthorizeRedirect(location, state);
    }

    private String generateState() {
        byte[] bytes = new byte[STATE_BYTES];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
