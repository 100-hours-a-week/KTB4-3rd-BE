package com.ktb.moyeota.domain.auth.store;

import static org.assertj.core.api.Assertions.assertThat;

import com.ktb.moyeota.domain.auth.model.SessionTokenView;
import java.time.LocalDateTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

public abstract class SessionStoreContractTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 1, 1, 0, 0);
    private static final LocalDateTime EXPIRES = NOW.plusDays(7);

    protected abstract SessionStore store();

    @Test
    @DisplayName("세션을 만들면 최초 토큰을 해시로 찾을 수 있다")
    void createThenFind() {
        Long sessionId = store().create(1L, "hash-a", EXPIRES);

        SessionTokenView view = store().findToken("hash-a").orElseThrow();

        assertThat(view.sessionId()).isEqualTo(sessionId);
        assertThat(view.userId()).isEqualTo(1L);
        assertThat(view.supersededAt()).isNull();
        assertThat(view.absoluteExpiresAt()).isEqualTo(EXPIRES);
    }

    @Test
    @DisplayName("없는 해시는 빈 값이다")
    void findMissing() {
        assertThat(store().findToken("nothing")).isEmpty();
    }

    @Test
    @DisplayName("회전은 한 번만 성공하고, 실패한 회전의 새 토큰은 만들어지지 않는다")
    void rotateOnlyOnce() {
        Long sessionId = store().create(1L, "hash-a", EXPIRES);

        assertThat(store().rotate(sessionId, "hash-a", "hash-b", NOW)).isTrue();
        assertThat(store().rotate(sessionId, "hash-a", "hash-lost-race", NOW.plusSeconds(1))).isFalse();

        assertThat(store().findToken("hash-lost-race")).isEmpty();
    }

    @Test
    @DisplayName("없는 토큰을 회전하면 실패하고 아무것도 만들지 않는다")
    void rotateMissing() {
        Long sessionId = store().create(1L, "hash-rotate-missing", EXPIRES);

        assertThat(store().rotate(sessionId, "nothing", "hash-never-issued", NOW)).isFalse();

        assertThat(store().findToken("nothing")).isEmpty();
        assertThat(store().findToken("hash-never-issued")).isEmpty();
    }

    @Test
    @DisplayName("회전하면 이전 토큰은 대체 시각을 갖고, 새 토큰은 같은 세션 · 사용자 · 만료 시각으로 생긴다")
    void rotateMarksOldAndIssuesNew() {
        Long sessionId = store().create(1L, "hash-a", EXPIRES);

        store().rotate(sessionId, "hash-a", "hash-b", NOW);

        assertThat(store().findToken("hash-a").orElseThrow().supersededAt()).isEqualTo(NOW);
        SessionTokenView rotated = store().findToken("hash-b").orElseThrow();
        assertThat(rotated.sessionId()).isEqualTo(sessionId);
        assertThat(rotated.userId()).isEqualTo(1L);
        assertThat(rotated.supersededAt()).isNull();
        assertThat(rotated.absoluteExpiresAt()).isEqualTo(EXPIRES);
    }

    @Test
    @DisplayName("사슬 전체가 남아 두 단계 지난 토큰도 찾을 수 있다")
    void keepsWholeChain() {
        Long sessionId = store().create(1L, "hash-a", EXPIRES);
        store().rotate(sessionId, "hash-a", "hash-b", NOW);
        store().rotate(sessionId, "hash-b", "hash-c", NOW.plusMinutes(30));

        assertThat(store().findToken("hash-a")).isPresent();
        assertThat(store().findToken("hash-b")).isPresent();
        assertThat(store().findToken("hash-c").orElseThrow().supersededAt()).isNull();
    }

    @Test
    @DisplayName("세션을 지우면 그 사슬의 모든 토큰이 함께 사라진다")
    void deleteSessionRemovesChain() {
        Long sessionId = store().create(1L, "hash-a", EXPIRES);
        store().rotate(sessionId, "hash-a", "hash-b", NOW);

        store().deleteSession(sessionId);

        assertThat(store().findToken("hash-a")).isEmpty();
        assertThat(store().findToken("hash-b")).isEmpty();
    }

    @Test
    @DisplayName("사용자의 세션을 모두 지우면 다른 사용자의 세션은 남는다")
    void deleteAllByUserIdKeepsOtherUsers() {
        Long deviceA = store().create(1L, "hash-a", EXPIRES);
        store().rotate(deviceA, "hash-a", "hash-a2", NOW);
        store().create(1L, "hash-b", EXPIRES);
        store().create(2L, "hash-other", EXPIRES);

        store().deleteAllByUserId(1L);

        assertThat(store().findToken("hash-a")).isEmpty();
        assertThat(store().findToken("hash-a2")).isEmpty();
        assertThat(store().findToken("hash-b")).isEmpty();
        assertThat(store().findToken("hash-other")).isPresent();
    }
}
