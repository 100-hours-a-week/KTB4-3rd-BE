package com.ktb.moyeota.domain.auth.service;

import com.ktb.moyeota.domain.auth.model.AuthorizeRedirect;
import com.ktb.moyeota.domain.auth.model.OAuthProvider;
import com.ktb.moyeota.global.external.kakao.KakaoOAuthClient;
import java.net.URI;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class OAuthLoginService {

    private final KakaoOAuthClient kakaoOAuthClient;
    private final OpaqueTokenFactory opaqueTokenFactory;

    public AuthorizeRedirect buildAuthorizeRedirect(OAuthProvider provider) {
        String state = opaqueTokenFactory.generate();
        URI location = switch (provider) {
            case KAKAO -> kakaoOAuthClient.buildAuthorizeUri(state);
        };
        return new AuthorizeRedirect(location, state);
    }
}
