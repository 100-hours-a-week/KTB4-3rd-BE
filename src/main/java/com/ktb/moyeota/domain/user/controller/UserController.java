package com.ktb.moyeota.domain.user.controller;

import com.ktb.moyeota.domain.auth.model.IssuedSession;
import com.ktb.moyeota.domain.auth.model.SignupSessionView;
import com.ktb.moyeota.domain.user.dto.SignupRequest;
import com.ktb.moyeota.domain.user.dto.SignupResponse;
import com.ktb.moyeota.domain.user.model.RegisteredUser;
import com.ktb.moyeota.domain.user.service.UserService;
import com.ktb.moyeota.global.common.ApiResponse;
import com.ktb.moyeota.global.security.cookie.AuthCookies;
import com.ktb.moyeota.global.security.resolver.SignupPrincipal;
import jakarta.validation.Valid;
import java.net.URI;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class UserController {

    private static final String SIGNED_UP = "가입이 완료되었어요";

    private final UserService userService;
    private final AuthCookies authCookies;

    @PostMapping("/users")
    public ResponseEntity<ApiResponse<SignupResponse>> signUp(
            @SignupPrincipal SignupSessionView signupSession,
            @Valid @RequestBody SignupRequest request) {

        RegisteredUser registered = userService.register(signupSession, request.toCommand());
        IssuedSession session = registered.session();

        return ResponseEntity.created(URI.create("/users/" + registered.userId()))
                .header(HttpHeaders.SET_COOKIE,
                        authCookies.refreshToken(session.refreshToken(), session.refreshTokenMaxAge())
                                .toString(),
                        authCookies.expiredSignupToken().toString())
                .body(ApiResponse.success(SIGNED_UP, SignupResponse.from(registered)));
    }
}
