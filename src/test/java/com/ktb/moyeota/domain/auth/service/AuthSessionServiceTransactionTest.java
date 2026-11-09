package com.ktb.moyeota.domain.auth.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.ktb.moyeota.domain.auth.model.IssuedSession;
import com.ktb.moyeota.domain.auth.model.ReissueResult;
import com.ktb.moyeota.domain.auth.model.SessionTokenView;
import com.ktb.moyeota.global.config.TestClockConfig;
import com.ktb.moyeota.global.security.AuthProperties;
import com.ktb.moyeota.global.security.jwt.AccessTokenProvider;
import com.ktb.moyeota.global.security.jwt.JwtConfig;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@DataJpaTest
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@Import({AuthSessionService.class, OpaqueTokenFactory.class, AccessTokenProvider.class, JwtConfig.class,
        TestClockConfig.class, AuthSessionServiceTransactionTest.Config.class})
@EnableConfigurationProperties(AuthProperties.class)
class AuthSessionServiceTransactionTest {

    @Autowired
    private AuthSessionService authSessionService;

    @Autowired
    private TransactionRecordingSessionStore sessionStore;

    @BeforeEach
    void clear() {
        sessionStore.calls.clear();
    }

    @Test
    @DisplayName("발급 · 재발급 · 로그아웃은 DB 트랜잭션을 열지 않고 저장소를 부른다")
    void doesNotOpenTransaction() {
        IssuedSession issued = authSessionService.issue(1L);
        ReissueResult.Rotated rotated = (ReissueResult.Rotated) authSessionService.reissue(issued.refreshToken());
        authSessionService.reissue(issued.refreshToken());
        authSessionService.revokeSession(rotated.refreshToken());

        assertThat(sessionStore.calls).isNotEmpty();
        assertThat(sessionStore.calls).allSatisfy(call -> assertThat(call.inTransaction()).as(call.method()).isFalse());
    }

    record Call(String method, boolean inTransaction) {
    }

    static class TransactionRecordingSessionStore extends InMemorySessionStore {

        final List<Call> calls = new CopyOnWriteArrayList<>();

        private void record(String method) {
            calls.add(new Call(method, TransactionSynchronizationManager.isActualTransactionActive()));
        }

        @Override
        public Long create(Long userId, String tokenHash, LocalDateTime absoluteExpiresAt) {
            record("create");
            return super.create(userId, tokenHash, absoluteExpiresAt);
        }

        @Override
        public Optional<SessionTokenView> findToken(String tokenHash) {
            record("findToken");
            return super.findToken(tokenHash);
        }

        @Override
        public synchronized boolean rotate(Long sessionId, String tokenHash, String newTokenHash, LocalDateTime now) {
            record("rotate");
            return super.rotate(sessionId, tokenHash, newTokenHash, now);
        }

        @Override
        public void deleteSession(Long sessionId) {
            record("deleteSession");
            super.deleteSession(sessionId);
        }
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class Config {

        @Bean
        TransactionRecordingSessionStore sessionStore() {
            return new TransactionRecordingSessionStore();
        }
    }
}
