package com.ktb.moyeota.domain.auth.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.ktb.moyeota.domain.auth.entity.SignupSession;
import com.ktb.moyeota.domain.auth.model.OAuthProvider;
import com.ktb.moyeota.domain.auth.model.OAuthUserProfile;
import com.ktb.moyeota.global.config.MutableClock;
import com.ktb.moyeota.global.config.TestClockConfig;
import java.time.LocalDateTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.context.annotation.Import;

@DataJpaTest
@Import({ExpiredSignupSessionCleanupService.class, TestClockConfig.class})
class ExpiredSignupSessionCleanupServiceTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 16, 12, 0);

    @Autowired
    private MutableClock clock;

    @Autowired
    private ExpiredSignupSessionCleanupService expiredSignupSessionCleanupService;

    @Autowired
    private TestEntityManager entityManager;

    @BeforeEach
    void setUp() {
        clock.set(NOW);
    }

    @Test
    @DisplayName("만료 시각이 된 회원가입 세션을 지운다")
    void deletesExpiredSignupSession() {
        SignupSession expired = signupSessionExpiringAt("expired", NOW);

        int deleted = expiredSignupSessionCleanupService.deleteExpired();

        entityManager.clear();
        assertThat(deleted).isEqualTo(1);
        assertThat(entityManager.find(SignupSession.class, expired.getTokenHash())).isNull();
    }

    @Test
    @DisplayName("아직 만료되지 않은 회원가입 세션은 남긴다")
    void keepsLiveSignupSession() {
        SignupSession live = signupSessionExpiringAt("live", NOW.plusSeconds(1));

        int deleted = expiredSignupSessionCleanupService.deleteExpired();

        entityManager.clear();
        assertThat(deleted).isZero();
        assertThat(entityManager.find(SignupSession.class, live.getTokenHash())).isNotNull();
    }

    private SignupSession signupSessionExpiringAt(String tokenHash, LocalDateTime expiresAt) {
        OAuthUserProfile profile = new OAuthUserProfile(OAuthProvider.KAKAO, "kakao-" + tokenHash, "가입자");
        return entityManager.persistAndFlush(SignupSession.open(tokenHash, profile, expiresAt));
    }
}
