package com.ktb.moyeota.domain.auth.store;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import com.ktb.moyeota.domain.auth.model.OAuthProvider;
import com.ktb.moyeota.domain.auth.model.OAuthUserProfile;
import com.ktb.moyeota.domain.auth.model.SignupSessionView;
import java.time.LocalDateTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

public abstract class SignupSessionStoreContractTest {

    private static final LocalDateTime EXPIRES = LocalDateTime.of(2026, 1, 1, 0, 15);
    private static final OAuthUserProfile PROFILE = new OAuthUserProfile(OAuthProvider.KAKAO, "1234567890");

    protected abstract SignupSessionStore store();

    @Test
    @DisplayName("만든 세션을 해시로 찾으면 카카오 회원번호와 만료 시각이 돌아온다")
    void createThenFind() {
        store().create("hash-a", PROFILE, EXPIRES);

        SignupSessionView view = store().find("hash-a").orElseThrow();

        assertThat(view.tokenHash()).isEqualTo("hash-a");
        assertThat(view.provider()).isEqualTo(OAuthProvider.KAKAO);
        assertThat(view.providerUserId()).isEqualTo("1234567890");
        assertThat(view.expiresAt()).isEqualTo(EXPIRES);
    }

    @Test
    @DisplayName("없는 해시는 빈 값이다")
    void findMissing() {
        assertThat(store().find("nothing")).isEmpty();
    }

    @Test
    @DisplayName("같은 카카오 회원이 가입을 마치기 전에 다시 로그인하면 세션이 하나 더 생긴다")
    void sameProviderUserCanOpenSeveralSessions() {
        store().create("hash-a", PROFILE, EXPIRES);
        store().create("hash-b", PROFILE, EXPIRES.plusMinutes(1));

        assertThat(store().find("hash-a")).isPresent();
        assertThat(store().find("hash-b")).isPresent();
    }

    @Test
    @DisplayName("지운 세션은 더 이상 찾을 수 없고 다른 세션은 남는다")
    void deleteRemovesOnlyThatSession() {
        store().create("hash-a", PROFILE, EXPIRES);
        store().create("hash-b", PROFILE, EXPIRES);

        store().delete("hash-a");

        assertThat(store().find("hash-a")).isEmpty();
        assertThat(store().find("hash-b")).isPresent();
    }

    @Test
    @DisplayName("없는 세션을 지워도 오류가 아니다")
    void deleteIsIdempotent() {
        assertThatCode(() -> store().delete("nothing")).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("만료 시각이 지났는지는 조회 결과가 스스로 판단한다")
    void viewKnowsExpiry() {
        store().create("hash-a", PROFILE, EXPIRES);

        SignupSessionView view = store().find("hash-a").orElseThrow();

        assertThat(view.isExpiredAt(EXPIRES.minusSeconds(1))).isFalse();
        assertThat(view.isExpiredAt(EXPIRES)).isTrue();
    }
}
