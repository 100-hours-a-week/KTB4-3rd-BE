package com.ktb.moyeota.domain.auth.controller;

import com.ktb.moyeota.domain.auth.model.AuthorizeRedirect;
import com.ktb.moyeota.domain.auth.model.OAuthProvider;
import com.ktb.moyeota.domain.auth.service.OAuthLoginService;
import com.ktb.moyeota.global.exception.BusinessException;
import com.ktb.moyeota.global.exception.CommonErrorCode;
import com.ktb.moyeota.global.security.cookie.AuthCookies;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class OAuthLoginController {

    private final OAuthLoginService oAuthLoginService;
    private final AuthCookies authCookies;

    @GetMapping("/auth/{provider}/login")
    public ResponseEntity<Void> startLogin(@PathVariable String provider) {
        OAuthProvider oAuthProvider = OAuthProvider.from(provider)
                .orElseThrow(() -> new BusinessException(CommonErrorCode.ENDPOINT_NOT_FOUND));

        AuthorizeRedirect redirect = oAuthLoginService.buildAuthorizeRedirect(oAuthProvider);
        return ResponseEntity.status(HttpStatus.FOUND)
                .location(redirect.location())
                .header(HttpHeaders.SET_COOKIE, authCookies.oauthState(redirect.state()).toString())
                .build();
    }
}
