package com.ktb.moyeota.domain.auth.store;

import static org.assertj.core.api.Assertions.assertThat;

import com.ktb.moyeota.domain.auth.entity.SignupSession;
import com.ktb.moyeota.domain.auth.model.OAuthProvider;
import com.ktb.moyeota.domain.auth.model.OAuthUserProfile;
import java.time.LocalDateTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.context.annotation.Import;

@DataJpaTest
@Import(JpaSignupSessionStore.class)
class JpaSignupSessionStoreTest {

    private static final LocalDateTime EXPIRES = LocalDateTime.of(2026, 1, 1, 0, 15);

    @Autowired
    private JpaSignupSessionStore signupSessionStore;

    @Autowired
    private TestEntityManager entityManager;

    @Test
    @DisplayName("카카오 회원번호와 만료 시각을 해시 키로 저장한다")
    void storesProviderUserIdUnderHash() {
        signupSessionStore.create(
                "hash-a", new OAuthUserProfile(OAuthProvider.KAKAO, "1234567890"), EXPIRES);
        entityManager.flush();
        entityManager.clear();

        SignupSession saved = entityManager.find(SignupSession.class, "hash-a");

        assertThat(saved.getProvider()).isEqualTo(OAuthProvider.KAKAO);
        assertThat(saved.getProviderUserId()).isEqualTo("1234567890");
        assertThat(saved.getExpiresAt()).isEqualTo(EXPIRES);
    }

    @Test
    @DisplayName("같은 카카오 회원이 가입을 마치기 전에 다시 로그인하면 세션이 하나 더 생긴다")
    void sameProviderUserCanOpenSeveralSessions() {
        OAuthUserProfile profile = new OAuthUserProfile(OAuthProvider.KAKAO, "1234567890");

        signupSessionStore.create("hash-a", profile, EXPIRES);
        signupSessionStore.create("hash-b", profile, EXPIRES.plusMinutes(1));
        entityManager.flush();
        entityManager.clear();

        assertThat(entityManager.find(SignupSession.class, "hash-a")).isNotNull();
        assertThat(entityManager.find(SignupSession.class, "hash-b")).isNotNull();
    }
}
