package com.ktb.moyeota.domain.auth.controller;

import com.ktb.moyeota.domain.auth.model.AuthorizeRedirect;
import com.ktb.moyeota.domain.auth.model.CallbackParams;
import com.ktb.moyeota.domain.auth.model.OAuthCallbackResult;
import com.ktb.moyeota.domain.auth.model.OAuthFront;
import com.ktb.moyeota.domain.auth.model.OAuthProvider;
import com.ktb.moyeota.domain.auth.service.OAuthFrontResolver;
import com.ktb.moyeota.domain.auth.service.OAuthLoginService;
import com.ktb.moyeota.global.exception.BusinessException;
import com.ktb.moyeota.global.exception.CommonErrorCode;
import com.ktb.moyeota.global.security.cookie.AuthCookies;
import java.net.URI;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
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
    private final OAuthFrontResolver oAuthFrontResolver;

    @GetMapping("/auth/{provider}/login")
    public ResponseEntity<Void> startLogin(
            @PathVariable String provider,
            @RequestParam(name = "front_origin", required = false) String frontOrigin) {

        OAuthProvider oAuthProvider = providerOf(provider);
        OAuthFront front = oAuthFrontResolver.resolve(oAuthProvider, frontOrigin);
        AuthorizeRedirect redirect = oAuthLoginService.buildAuthorizeRedirect(oAuthProvider, front);
        ResponseCookie frontCookie = front.isDefault()
                ? authCookies.expiredOauthFront()
                : authCookies.oauthFront(front.origin());
        return redirect(
                redirect.location(),
                authCookies.oauthState(redirect.state()).toString(),
                frontCookie.toString());
    }

    @GetMapping("/auth/{provider}/callback")
    public ResponseEntity<Void> callback(
            @PathVariable String provider,
            @RequestParam(required = false) String code,
            @RequestParam(required = false) String state,
            @RequestParam(required = false) String error,
            @CookieValue(name = AuthCookies.OAUTH_STATE, required = false) String oauthStateCookie,
            @CookieValue(name = AuthCookies.OAUTH_FRONT, required = false) String oauthFrontCookie) {

        OAuthProvider oAuthProvider = providerOf(provider);
        OAuthFront front = oAuthFrontResolver.resolve(oAuthProvider, oauthFrontCookie);
        CallbackParams params = new CallbackParams(code, state, error, oauthStateCookie);
        String expiredState = authCookies.expiredOauthState().toString();
        String expiredFront = authCookies.expiredOauthFront().toString();

        return switch (oAuthLoginService.handleCallback(oAuthProvider, params, front)) {
            case OAuthCallbackResult.Existing existing -> redirect(
                    frontCallback(front, "status", STATUS_OK),
                    expiredState,
                    expiredFront,
                    authCookies.refreshToken(existing.refreshToken(), existing.refreshTokenMaxAge())
                            .toString());
            case OAuthCallbackResult.SignupRequired signup -> redirect(
                    frontCallback(front, "status", STATUS_SIGNUP_REQUIRED),
                    expiredState,
                    expiredFront,
                    authCookies.signupToken(signup.signupToken(), signup.signupTokenMaxAge())
                            .toString());
            case OAuthCallbackResult.Failed failed -> redirect(
                    frontCallback(front, "error", failed.error().name()),
                    expiredState,
                    expiredFront);
        };
    }

    private OAuthProvider providerOf(String provider) {
        return OAuthProvider.from(provider)
                .orElseThrow(() -> new BusinessException(CommonErrorCode.ENDPOINT_NOT_FOUND));
    }

    private URI frontCallback(OAuthFront front, String name, String value) {
        return UriComponentsBuilder.fromUriString(front.callbackUri())
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
