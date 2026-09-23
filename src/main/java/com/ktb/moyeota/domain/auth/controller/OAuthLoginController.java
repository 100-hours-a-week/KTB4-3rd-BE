package com.ktb.moyeota.domain.auth.controller;

import com.ktb.moyeota.domain.auth.model.AuthorizeRedirect;
import com.ktb.moyeota.domain.auth.model.CallbackParams;
import com.ktb.moyeota.domain.auth.model.OAuthCallbackResult;
import com.ktb.moyeota.domain.auth.model.OAuthProvider;
import com.ktb.moyeota.domain.auth.service.OAuthLoginService;
import com.ktb.moyeota.global.config.OAuthProperties;
import com.ktb.moyeota.global.exception.BusinessException;
import com.ktb.moyeota.global.exception.CommonErrorCode;
import com.ktb.moyeota.global.security.cookie.AuthCookies;
import java.net.URI;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.UriComponentsBuilder;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class OAuthLoginController {

    private static final String STATUS_OK = "ok";
    private static final String STATUS_SIGNUP_REQUIRED = "signup_required";

    private final OAuthLoginService oAuthLoginService;
    private final AuthCookies authCookies;
    private final OAuthProperties oAuthProperties;

    @GetMapping("/auth/{provider}/login")
    public ResponseEntity<Void> startLogin(@PathVariable String provider) {
        AuthorizeRedirect redirect = oAuthLoginService.buildAuthorizeRedirect(providerOf(provider));
        return ResponseEntity.status(HttpStatus.FOUND)
                .location(redirect.location())
                .header(HttpHeaders.SET_COOKIE, authCookies.oauthState(redirect.state()).toString())
                .build();
    }

    @GetMapping("/auth/{provider}/callback")
    public ResponseEntity<Void> callback(
            @PathVariable String provider,
            @RequestParam(required = false) String code,
            @RequestParam(required = false) String state,
            @RequestParam(required = false) String error,
            @CookieValue(name = AuthCookies.OAUTH_STATE, required = false) String oauthStateCookie) {

        CallbackParams params = new CallbackParams(code, state, error, oauthStateCookie);
        String expiredState = authCookies.expiredOauthState().toString();

        return switch (oAuthLoginService.handleCallback(providerOf(provider), params)) {
            case OAuthCallbackResult.Existing existing -> redirect(
                    frontCallback("status", STATUS_OK),
                    expiredState,
                    authCookies.refreshToken(existing.refreshToken(), existing.refreshTokenMaxAge())
                            .toString());
            case OAuthCallbackResult.SignupRequired signup -> redirect(
                    frontCallback("status", STATUS_SIGNUP_REQUIRED),
                    expiredState,
                    authCookies.signupToken(signup.signupToken(), signup.signupTokenMaxAge())
                            .toString());
            case OAuthCallbackResult.Failed failed -> redirect(
                    frontCallback("error", failed.error().name()),
                    expiredState);
        };
    }

    private OAuthProvider providerOf(String provider) {
        return OAuthProvider.from(provider)
                .orElseThrow(() -> new BusinessException(CommonErrorCode.ENDPOINT_NOT_FOUND));
    }

    private URI frontCallback(String name, String value) {
        return UriComponentsBuilder.fromUriString(oAuthProperties.frontCallbackUri())
                .queryParam(name, value)
                .build()
                .toUri();
    }

    private ResponseEntity<Void> redirect(URI location, String... cookies) {
        return ResponseEntity.status(HttpStatus.FOUND)
                .location(location)
                .header(HttpHeaders.SET_COOKIE, cookies)
                .build();
    }
}
