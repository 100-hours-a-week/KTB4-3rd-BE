package com.ktb.moyeota.domain.user.controller;

import com.ktb.moyeota.domain.auth.model.IssuedSession;
import com.ktb.moyeota.domain.auth.model.SignupSessionView;
import com.ktb.moyeota.domain.user.dto.NicknameAvailabilityResponse;
import com.ktb.moyeota.domain.user.dto.SignupRequest;
import com.ktb.moyeota.domain.user.dto.SignupResponse;
import com.ktb.moyeota.domain.user.model.RegisteredUser;
import com.ktb.moyeota.domain.user.service.UserService;
import com.ktb.moyeota.domain.user.validation.NicknameRules;
import com.ktb.moyeota.global.common.ApiResponse;
import com.ktb.moyeota.global.security.cookie.AuthCookies;
import com.ktb.moyeota.global.security.resolver.SignupPrincipal;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.net.URI;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class UserController {

    private static final String SIGNED_UP = "가입이 완료되었어요";
    private static final String NICKNAME_AVAILABLE = "사용할 수 있는 닉네임이에요";
    private static final String NICKNAME_TAKEN = "이미 사용 중인 닉네임이에요";

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

    @GetMapping("/users/nickname-availability")
    public ApiResponse<NicknameAvailabilityResponse> checkNickname(
            @RequestParam
            @Size(min = NicknameRules.MIN_LENGTH, max = NicknameRules.MAX_LENGTH)
            @Pattern(regexp = NicknameRules.PATTERN)
            String nickname) {

        boolean available = userService.isNicknameAvailable(nickname);
        return ApiResponse.success(
                available ? NICKNAME_AVAILABLE : NICKNAME_TAKEN,
                new NicknameAvailabilityResponse(available));
    }
}
