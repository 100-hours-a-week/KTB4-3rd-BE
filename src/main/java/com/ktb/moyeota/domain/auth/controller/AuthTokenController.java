package com.ktb.moyeota.domain.auth.controller;

import com.ktb.moyeota.domain.auth.dto.AccessTokenResponse;
import com.ktb.moyeota.domain.auth.model.ReissueResult;
import com.ktb.moyeota.domain.auth.service.AuthSessionService;
import com.ktb.moyeota.global.common.ApiResponse;
import com.ktb.moyeota.global.exception.BusinessException;
import com.ktb.moyeota.global.exception.CommonErrorCode;
import com.ktb.moyeota.global.security.cookie.AuthCookies;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class AuthTokenController {

    private static final String REISSUED = "액세스 토큰이 재발급되었습니다.";

    private final AuthSessionService authSessionService;
    private final AuthCookies authCookies;

    @PostMapping("/auth/tokens")
    public ResponseEntity<ApiResponse<AccessTokenResponse>> reissue(
            @CookieValue(name = AuthCookies.REFRESH_TOKEN, required = false) String refreshToken,
            HttpServletResponse response) {

        return switch (authSessionService.reissue(refreshToken)) {
            case ReissueResult.Rotated rotated -> ResponseEntity.ok()
                    .header(HttpHeaders.SET_COOKIE, authCookies
                            .refreshToken(rotated.refreshToken(), rotated.refreshTokenMaxAge())
                            .toString())
                    .body(ApiResponse.success(REISSUED, AccessTokenResponse.from(rotated.accessToken())));
            case ReissueResult.Graced graced -> ResponseEntity.ok()
                    .body(ApiResponse.success(REISSUED, AccessTokenResponse.from(graced.accessToken())));
            case ReissueResult.Rejected ignored -> throw rejected(response);
        };
    }

    @DeleteMapping("/auth/sessions")
    public ResponseEntity<Void> logout(
            @CookieValue(name = AuthCookies.REFRESH_TOKEN, required = false) String refreshToken) {

        authSessionService.revokeSession(refreshToken);
        return ResponseEntity.noContent()
                .header(HttpHeaders.SET_COOKIE, authCookies.expiredRefreshToken().toString())
                .build();
    }

    private BusinessException rejected(HttpServletResponse response) {
        response.addHeader(HttpHeaders.SET_COOKIE, authCookies.expiredRefreshToken().toString());
        return new BusinessException(CommonErrorCode.UNAUTHORIZED);
    }
}
