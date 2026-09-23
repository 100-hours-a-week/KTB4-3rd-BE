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
    @DisplayName("대체(사용 완료) 표시는 한 번만 성공한다")
    void markSupersededOnlyOnce() {
        store().create(1L, "hash-a", EXPIRES);

        assertThat(store().markSuperseded("hash-a", NOW)).isTrue();
        assertThat(store().markSuperseded("hash-a", NOW.plusSeconds(1))).isFalse();
    }

    @Test
    @DisplayName("대체(사용 완료)된 토큰은 조회 시 대체 시각을 갖는다")
    void supersededAtIsVisible() {
        store().create(1L, "hash-a", EXPIRES);
        store().markSuperseded("hash-a", NOW);

        assertThat(store().findToken("hash-a").orElseThrow().supersededAt()).isEqualTo(NOW);
    }

    @Test
    @DisplayName("사슬 전체가 남아 두 단계 지난 토큰도 찾을 수 있다")
    void keepsWholeChain() {
        Long sessionId = store().create(1L, "hash-a", EXPIRES);
        store().markSuperseded("hash-a", NOW);
        store().appendToken(sessionId, "hash-b");
        store().markSuperseded("hash-b", NOW.plusMinutes(30));
        store().appendToken(sessionId, "hash-c");

        assertThat(store().findToken("hash-a")).isPresent();
        assertThat(store().findToken("hash-b")).isPresent();
        assertThat(store().findToken("hash-c").orElseThrow().supersededAt()).isNull();
    }

    @Test
    @DisplayName("세션을 지우면 그 사슬의 모든 토큰이 함께 사라진다")
    void deleteSessionRemovesChain() {
        Long sessionId = store().create(1L, "hash-a", EXPIRES);
        store().appendToken(sessionId, "hash-b");

        store().deleteSession(sessionId);

        assertThat(store().findToken("hash-a")).isEmpty();
        assertThat(store().findToken("hash-b")).isEmpty();
    }
}
