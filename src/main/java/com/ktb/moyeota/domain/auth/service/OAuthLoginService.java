package com.ktb.moyeota.domain.auth.service;

import com.ktb.moyeota.domain.auth.model.AuthorizeRedirect;
import com.ktb.moyeota.domain.auth.model.OAuthProvider;
import com.ktb.moyeota.global.external.kakao.KakaoOAuthClient;
import java.net.URI;
import java.security.SecureRandom;
import java.util.Base64;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class OAuthLoginService {

    private static final int STATE_BYTES = 32;

    private final SecureRandom secureRandom = new SecureRandom();

    private final KakaoOAuthClient kakaoOAuthClient;

    public AuthorizeRedirect buildAuthorizeRedirect(OAuthProvider provider) {
        String state = generateState();
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
