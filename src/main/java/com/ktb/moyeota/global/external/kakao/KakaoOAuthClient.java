package com.ktb.moyeota.global.external.kakao;

import com.ktb.moyeota.domain.auth.error.OAuthLoginError;
import com.ktb.moyeota.domain.auth.error.OAuthLoginException;
import com.ktb.moyeota.domain.auth.model.OAuthProvider;
import com.ktb.moyeota.domain.auth.model.OAuthUserProfile;
import java.net.URI;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.util.UriComponentsBuilder;

@Slf4j
@Component
@RequiredArgsConstructor
public class KakaoOAuthClient {

    private static final String RESPONSE_TYPE = "code";
    private static final String GRANT_TYPE = "authorization_code";
    private static final String INVALID_GRANT = "invalid_grant";

    private final KakaoProperties kakaoProperties;
    private final RestClient kakaoRestClient;

    public URI buildAuthorizeUri(String state, String redirectUri) {
        return UriComponentsBuilder.fromUriString(kakaoProperties.authorizeUri())
                .queryParam("client_id", "{clientId}")
                .queryParam("redirect_uri", "{redirectUri}")
                .queryParam("response_type", "{responseType}")
                .queryParam("state", "{state}")
                .encode()
                .buildAndExpand(
                        kakaoProperties.clientId(),
                        redirectUri,
                        RESPONSE_TYPE,
                        state)
                .toUri();
    }

    public OAuthUserProfile fetchProfile(String code, String redirectUri) {
        KakaoUserResponse user = fetchUser(exchangeToken(code, redirectUri));
        if (user.nickname() == null) {
            log.error("[KAKAO_NICKNAME_MISSING] 닉네임 동의항목이 필수 동의로 설정됐는지 확인하세요.");
            throw new OAuthLoginException(OAuthLoginError.OAUTH_UNAVAILABLE);
        }
        return new OAuthUserProfile(OAuthProvider.KAKAO, String.valueOf(user.id()), user.nickname());
    }

    private String exchangeToken(String code, String redirectUri) {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("grant_type", GRANT_TYPE);
        form.add("client_id", kakaoProperties.clientId());
        form.add("client_secret", kakaoProperties.clientSecret());
        form.add("redirect_uri", redirectUri);
        form.add("code", code);

        try {
            KakaoTokenResponse response = kakaoRestClient.post()
                    .uri(kakaoProperties.tokenUri())
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(form)
                    .retrieve()
                    .body(KakaoTokenResponse.class);
            if (response == null || response.accessToken() == null) {
                throw unavailable("카카오 토큰 응답에 access_token이 없습니다.", null);
            }
            return response.accessToken();
        } catch (HttpClientErrorException e) {
            throw tokenRejected(e);
        } catch (RestClientException e) {
            throw unavailable("카카오 토큰 교환에 실패했습니다.", e);
        }
    }

    private KakaoUserResponse fetchUser(String kakaoAccessToken) {
        try {
            KakaoUserResponse response = kakaoRestClient.get()
                    .uri(kakaoProperties.userInfoUri())
                    .headers(headers -> headers.setBearerAuth(kakaoAccessToken))
                    .retrieve()
                    .body(KakaoUserResponse.class);
            if (response == null || response.id() == null) {
                throw unavailable("카카오 사용자 응답에 id가 없습니다.", null);
            }
            return response;
        } catch (RestClientException e) {
            throw unavailable("카카오 사용자 정보 조회에 실패했습니다.", e);
        }
    }

    private OAuthLoginException tokenRejected(HttpClientErrorException e) {
        KakaoErrorResponse body = errorBodyOf(e);
        if (body != null && INVALID_GRANT.equals(body.error())) {
            return new OAuthLoginException(OAuthLoginError.INVALID_OAUTH_CODE, e);
        }
        log.error("[KAKAO_TOKEN_REJECTED] status={} error={} errorCode={}",
                e.getStatusCode().value(),
                body == null ? null : body.error(),
                body == null ? null : body.errorCode());
        return new OAuthLoginException(OAuthLoginError.OAUTH_UNAVAILABLE, e);
    }

    private KakaoErrorResponse errorBodyOf(HttpClientErrorException e) {
        try {
            return e.getResponseBodyAs(KakaoErrorResponse.class);
        } catch (RuntimeException parseFailure) {
            return null;
        }
    }

    private OAuthLoginException unavailable(String message, Throwable cause) {
        log.warn("[KAKAO_UNAVAILABLE] {}", message, cause);
        return new OAuthLoginException(OAuthLoginError.OAUTH_UNAVAILABLE, cause);
    }
}
