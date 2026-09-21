package com.ktb.moyeota.domain.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.ktb.moyeota.domain.auth.error.OAuthLoginError;
import com.ktb.moyeota.domain.auth.error.OAuthLoginException;
import com.ktb.moyeota.domain.auth.model.AuthorizeRedirect;
import com.ktb.moyeota.domain.auth.model.CallbackParams;
import com.ktb.moyeota.domain.auth.model.IssuedSession;
import com.ktb.moyeota.domain.auth.model.OAuthCallbackResult;
import com.ktb.moyeota.domain.auth.model.OAuthProvider;
import com.ktb.moyeota.domain.auth.model.OAuthUserProfile;
import com.ktb.moyeota.domain.auth.repository.OAuthAccountRepository;
import com.ktb.moyeota.domain.auth.store.SignupSessionStore;
import com.ktb.moyeota.global.external.kakao.KakaoOAuthClient;
import com.ktb.moyeota.global.security.AuthProperties;
import com.ktb.moyeota.global.security.jwt.AccessToken;
import java.net.URI;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class OAuthLoginServiceTest {

    private static final Instant NOW = Instant.parse("2026-01-01T00:00:00Z");
    private static final Duration SIGNUP_TTL = Duration.ofMinutes(15);
    private static final String STATE = "state-value";
    private static final String CODE = "auth-code";
    private static final OAuthUserProfile PROFILE =
            new OAuthUserProfile(OAuthProvider.KAKAO, "1234567890");

    private final KakaoOAuthClient kakaoOAuthClient = mock(KakaoOAuthClient.class);
    private final OpaqueTokenFactory opaqueTokenFactory = new OpaqueTokenFactory();
    private final OAuthAccountRepository oAuthAccountRepository = mock(OAuthAccountRepository.class);
    private final SignupSessionStore signupSessionStore = mock(SignupSessionStore.class);
    private final AuthSessionService authSessionService = mock(AuthSessionService.class);

    private final OAuthLoginService service = new OAuthLoginService(
            kakaoOAuthClient,
            opaqueTokenFactory,
            oAuthAccountRepository,
            signupSessionStore,
            authSessionService,
            new AuthProperties(
                    new AuthProperties.Jwt(
                            "test-only-moyeota-access-token-secret-0123456789", "moyeota",
                            Duration.ofMinutes(30)),
                    new AuthProperties.Refresh(Duration.ofDays(7), Duration.ofSeconds(10)),
                    new AuthProperties.Signup(SIGNUP_TTL),
                    new AuthProperties.Cookie(false)),
            Clock.fixed(NOW, ZoneOffset.UTC));

    @Nested
    @DisplayName("로그인 진입")
    class BuildAuthorizeRedirect {

        @Test
        @DisplayName("state는 32바이트 난수의 base64url 표현이고 호출마다 달라진다")
        void stateIsRandomAndUrlSafe() {
            given(kakaoOAuthClient.buildAuthorizeUri(any())).willReturn(URI.create("https://kauth"));

            String first = service.buildAuthorizeRedirect(OAuthProvider.KAKAO).state();
            String second = service.buildAuthorizeRedirect(OAuthProvider.KAKAO).state();

            assertThat(first).hasSize(43).matches("^[A-Za-z0-9_-]+$");
            assertThat(first).isNotEqualTo(second);
        }

        @Test
        @DisplayName("쿠키에 담길 state를 그대로 인가 URI 조립에 넘긴다")
        void passesSameStateToClient() {
            given(kakaoOAuthClient.buildAuthorizeUri(any())).willReturn(URI.create("https://kauth"));

            AuthorizeRedirect redirect = service.buildAuthorizeRedirect(OAuthProvider.KAKAO);

            verify(kakaoOAuthClient).buildAuthorizeUri(redirect.state());
            assertThat(redirect.location()).isEqualTo(URI.create("https://kauth"));
        }
    }

    @Nested
    @DisplayName("state 검증")
    class StateValidation {

        @Test
        @DisplayName("쿠키가 없으면 INVALID_STATE이고 카카오를 부르지 않는다")
        void missingCookie() {
            assertFailed(callback(CODE, STATE, null, null), OAuthLoginError.INVALID_STATE);
            verifyNoInteractions(kakaoOAuthClient);
        }

        @Test
        @DisplayName("쿼리에 state가 없으면 INVALID_STATE다")
        void missingQueryState() {
            assertFailed(callback(CODE, null, null, STATE), OAuthLoginError.INVALID_STATE);
        }

        @Test
        @DisplayName("쿠키와 쿼리가 다르면 INVALID_STATE다")
        void mismatch() {
            assertFailed(callback(CODE, "forged", null, STATE), OAuthLoginError.INVALID_STATE);
            verifyNoInteractions(kakaoOAuthClient);
        }

        @Test
        @DisplayName("빈 문자열끼리 일치해도 통과시키지 않는다")
        void blankValuesNeverMatch() {
            assertFailed(callback(CODE, "", null, ""), OAuthLoginError.INVALID_STATE);
        }

        @Test
        @DisplayName("state가 틀리면 동의 취소 응답이어도 INVALID_STATE가 먼저다")
        void stateIsCheckedBeforeConsent() {
            assertFailed(callback(null, "forged", "access_denied", STATE), OAuthLoginError.INVALID_STATE);
        }
    }

    @Nested
    @DisplayName("카카오 응답 처리")
    class KakaoOutcome {

        @Test
        @DisplayName("사용자가 동의를 취소하면 CONSENT_DENIED다")
        void consentDenied() {
            assertFailed(callback(null, STATE, "access_denied", STATE), OAuthLoginError.CONSENT_DENIED);
            verifyNoInteractions(kakaoOAuthClient);
        }

        @Test
        @DisplayName("인가 코드가 없으면 INVALID_OAUTH_CODE다")
        void missingCode() {
            assertFailed(callback(" ", STATE, null, STATE), OAuthLoginError.INVALID_OAUTH_CODE);
            verifyNoInteractions(kakaoOAuthClient);
        }

        @Test
        @DisplayName("카카오 클라이언트의 실패 사유를 그대로 전달한다")
        void propagatesClientFailure() {
            given(kakaoOAuthClient.fetchProfile(CODE))
                    .willThrow(new OAuthLoginException(OAuthLoginError.OAUTH_UNAVAILABLE));

            assertFailed(callback(CODE, STATE, null, STATE), OAuthLoginError.OAUTH_UNAVAILABLE);
            verifyNoInteractions(authSessionService, signupSessionStore);
        }
    }

    @Nested
    @DisplayName("회원 판정")
    class MemberDecision {

        @Test
        @DisplayName("기존 회원이면 로그인 세션을 발급하고 회원가입 세션은 만들지 않는다")
        void existingMemberLogsIn() {
            given(kakaoOAuthClient.fetchProfile(CODE)).willReturn(PROFILE);
            given(oAuthAccountRepository.findActiveUserId(OAuthProvider.KAKAO, "1234567890"))
                    .willReturn(Optional.of(42L));
            given(authSessionService.issue(42L)).willReturn(new IssuedSession(
                    new AccessToken("access-value", 1800), "refresh-value", Duration.ofDays(7)));

            OAuthCallbackResult result = service.handleCallback(
                    OAuthProvider.KAKAO, callback(CODE, STATE, null, STATE));

            assertThat(result).isEqualTo(
                    new OAuthCallbackResult.Existing("refresh-value", Duration.ofDays(7)));
            verifyNoInteractions(signupSessionStore);
        }

        @Test
        @DisplayName("신규면 회원가입 세션을 해시로 저장하고 원문 토큰을 돌려준다")
        void newcomerOpensSignupSession() {
            given(kakaoOAuthClient.fetchProfile(CODE)).willReturn(PROFILE);
            given(oAuthAccountRepository.findActiveUserId(OAuthProvider.KAKAO, "1234567890"))
                    .willReturn(Optional.empty());

            OAuthCallbackResult result = service.handleCallback(
                    OAuthProvider.KAKAO, callback(CODE, STATE, null, STATE));

            assertThat(result).isInstanceOf(OAuthCallbackResult.SignupRequired.class);
            OAuthCallbackResult.SignupRequired signup = (OAuthCallbackResult.SignupRequired) result;
            assertThat(signup.signupToken()).hasSize(43);
            assertThat(signup.signupTokenMaxAge()).isEqualTo(SIGNUP_TTL);

            ArgumentCaptor<String> hash = ArgumentCaptor.forClass(String.class);
            ArgumentCaptor<LocalDateTime> expiresAt = ArgumentCaptor.forClass(LocalDateTime.class);
            verify(signupSessionStore).create(hash.capture(), any(), expiresAt.capture());
            assertThat(hash.getValue())
                    .isEqualTo(opaqueTokenFactory.hash(signup.signupToken()))
                    .isNotEqualTo(signup.signupToken());
            assertThat(expiresAt.getValue())
                    .isEqualTo(LocalDateTime.ofInstant(NOW, ZoneOffset.UTC).plus(SIGNUP_TTL));
            verify(authSessionService, never()).issue(any());
        }

        @Test
        @DisplayName("회원가입 세션은 카카오 회원번호를 기억한다")
        void signupSessionCarriesProviderUserId() {
            given(kakaoOAuthClient.fetchProfile(CODE)).willReturn(PROFILE);
            given(oAuthAccountRepository.findActiveUserId(any(), any())).willReturn(Optional.empty());

            service.handleCallback(OAuthProvider.KAKAO, callback(CODE, STATE, null, STATE));

            ArgumentCaptor<OAuthUserProfile> profile = ArgumentCaptor.forClass(OAuthUserProfile.class);
            verify(signupSessionStore).create(any(), profile.capture(), any());
            assertThat(profile.getValue()).isEqualTo(PROFILE);
        }
    }

    private CallbackParams callback(String code, String state, String error, String stateCookie) {
        return new CallbackParams(code, state, error, stateCookie);
    }

    private void assertFailed(CallbackParams params, OAuthLoginError expected) {
        assertThat(service.handleCallback(OAuthProvider.KAKAO, params))
                .isEqualTo(new OAuthCallbackResult.Failed(expected));
    }
}
