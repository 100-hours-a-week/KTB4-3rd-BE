package com.ktb.moyeota.domain.auth.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.ktb.moyeota.domain.auth.model.ReissueResult;
import com.ktb.moyeota.domain.auth.store.SessionStore;
import com.ktb.moyeota.global.security.AuthProperties;
import com.ktb.moyeota.global.security.jwt.AccessTokenProvider;
import com.ktb.moyeota.global.security.jwt.JwtConfig;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class AuthSessionServiceTest {

    private static final Long USER_ID = 42L;
    private static final Instant START = Instant.parse("2026-01-01T00:00:00Z");
    private static final ZoneId ZONE = ZoneId.of("UTC");
    private static final Duration GRACE = Duration.ofSeconds(10);
    private static final Duration REFRESH_TTL = Duration.ofDays(7);

    private final SessionStore sessionStore = new InMemorySessionStore();
    private final OpaqueTokenFactory opaqueTokenFactory = new OpaqueTokenFactory();
    private MutableClock clock;
    private AuthSessionService service;

    @BeforeEach
    void setUp() {
        clock = new MutableClock(START);
        AuthProperties properties = new AuthProperties(
                new AuthProperties.Jwt(
                        "test-only-moyeota-access-token-secret-0123456789", "moyeota",
                        Duration.ofMinutes(30)),
                new AuthProperties.Refresh(REFRESH_TTL, GRACE),
                new AuthProperties.Signup(Duration.ofMinutes(15)),
                new AuthProperties.Cookie(false));
        JwtConfig jwtConfig = new JwtConfig();
        AccessTokenProvider accessTokenProvider = new AccessTokenProvider(
                jwtConfig.jwtEncoder(jwtConfig.jwtSecretKey(properties)), properties, clock);
        service = new AuthSessionService(
                sessionStore, opaqueTokenFactory, accessTokenProvider, properties, clock);
    }

    private LocalDateTime supersededAtOf(String refreshToken) {
        return sessionStore.findToken(opaqueTokenFactory.hash(refreshToken))
                .orElseThrow()
                .supersededAt();
    }

    private String openSession() {
        String refreshToken = opaqueTokenFactory.generate();
        sessionStore.create(USER_ID, opaqueTokenFactory.hash(refreshToken),
                LocalDateTime.now(clock).plus(REFRESH_TTL));
        return refreshToken;
    }

    @Nested
    @DisplayName("재발급")
    class Reissue {

        @Test
        @DisplayName("현재 토큰이면 회전해서 새 리프레시 토큰을 준다")
        void rotatesCurrentToken() {
            String first = openSession();

            ReissueResult result = service.reissue(first);

            assertThat(result).isInstanceOf(ReissueResult.Rotated.class);
            ReissueResult.Rotated rotated = (ReissueResult.Rotated) result;
            assertThat(rotated.refreshToken()).isNotEqualTo(first);
            assertThat(supersededAtOf(first)).isNotNull();
            assertThat(supersededAtOf(rotated.refreshToken())).isNull();
        }

        @Test
        @DisplayName("회전한 새 토큰으로 다시 회전할 수 있다")
        void rotatesRepeatedly() {
            String token = openSession();

            for (int i = 0; i < 3; i++) {
                ReissueResult result = service.reissue(token);
                assertThat(result).isInstanceOf(ReissueResult.Rotated.class);
                token = ((ReissueResult.Rotated) result).refreshToken();
                clock.advance(Duration.ofMinutes(30));
            }
        }

        @Test
        @DisplayName("grace 안에 옛 토큰이 오면 액세스 토큰만 주고 회전하지 않는다")
        void graceReturnsAccessTokenOnly() {
            String first = openSession();
            String second = ((ReissueResult.Rotated) service.reissue(first)).refreshToken();

            clock.advance(Duration.ofSeconds(9));
            ReissueResult result = service.reissue(first);

            assertThat(result).isInstanceOf(ReissueResult.Graced.class);
            assertThat(supersededAtOf(second)).isNull();
        }

        @Test
        @DisplayName("grace 경계(10초)까지는 허용한다")
        void graceBoundaryIsInclusive() {
            String first = openSession();
            service.reissue(first);

            clock.advance(GRACE);

            assertThat(service.reissue(first)).isInstanceOf(ReissueResult.Graced.class);
        }

        @Test
        @DisplayName("grace를 넘겨 옛 토큰이 오면 재사용으로 보고 세션을 폐기한다")
        void reuseRevokesSession() {
            String first = openSession();
            String second = ((ReissueResult.Rotated) service.reissue(first)).refreshToken();

            clock.advance(GRACE.plusSeconds(1));
            ReissueResult result = service.reissue(first);

            assertThat(result).isInstanceOf(ReissueResult.Rejected.class);
            assertThat(service.reissue(second)).isInstanceOf(ReissueResult.Rejected.class);
        }

        @Test
        @DisplayName("절대 만료를 넘긴 세션은 회전할 수 없고 폐기된다")
        void expiredSessionIsRejected() {
            String token = openSession();

            clock.advance(REFRESH_TTL);

            assertThat(service.reissue(token)).isInstanceOf(ReissueResult.Rejected.class);
            assertThat(sessionStore.findToken(opaqueTokenFactory.hash(token))).isEmpty();
        }

        @Test
        @DisplayName("모르는 토큰과 빈 값은 거부한다")
        void unknownTokenIsRejected() {
            assertThat(service.reissue("never-issued")).isInstanceOf(ReissueResult.Rejected.class);
            assertThat(service.reissue(null)).isInstanceOf(ReissueResult.Rejected.class);
            assertThat(service.reissue("  ")).isInstanceOf(ReissueResult.Rejected.class);
        }
    }

    @Nested
    @DisplayName("세션 폐기")
    class Revoke {

        @Test
        @DisplayName("폐기하면 그 리프레시 토큰 체인 패밀리의 토큰이 모두 거부된다")
        void revokeRejectsWholeChain() {
            String first = openSession();
            String second = ((ReissueResult.Rotated) service.reissue(first)).refreshToken();

            service.revokeSession(second);

            assertThat(service.reissue(second)).isInstanceOf(ReissueResult.Rejected.class);
            assertThat(service.reissue(first)).isInstanceOf(ReissueResult.Rejected.class);
        }

        @Test
        @DisplayName("옛 토큰으로 폐기해도 세션 전체가 사라진다")
        void revokeBySupersededToken() {
            String first = openSession();
            String second = ((ReissueResult.Rotated) service.reissue(first)).refreshToken();

            service.revokeSession(first);

            assertThat(sessionStore.findToken(opaqueTokenFactory.hash(first))).isEmpty();
            assertThat(sessionStore.findToken(opaqueTokenFactory.hash(second))).isEmpty();
        }

        @Test
        @DisplayName("다른 기기 세션은 살아 있다")
        void revokeDoesNotAffectOtherSessions() {
            String deviceA = openSession();
            String deviceB = openSession();

            service.revokeSession(deviceA);

            assertThat(service.reissue(deviceB)).isInstanceOf(ReissueResult.Rotated.class);
        }

        @Test
        @DisplayName("없는 토큰, 빈 값, 이미 폐기한 세션 모두 예외 없이 끝난다")
        void revokeIsIdempotent() {
            String token = openSession();

            service.revokeSession(token);
            service.revokeSession(token);
            service.revokeSession(null);
            service.revokeSession("  ");
            service.revokeSession("never-issued");
        }
    }

    static class MutableClock extends Clock {

        private Instant instant;

        MutableClock(Instant instant) {
            this.instant = instant;
        }

        void advance(Duration duration) {
            instant = instant.plus(duration);
        }

        @Override
        public ZoneId getZone() {
            return ZONE;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return instant;
        }
    }
}
