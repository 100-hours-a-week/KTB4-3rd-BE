package com.ktb.moyeota.domain.auth.store;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@DataJpaTest
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@Import(JpaSessionStore.class)
class JpaSessionStoreTransactionTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 1, 1, 0, 0);

    @Autowired
    private JpaSessionStore sessionStore;

    @Test
    @DisplayName("묶은 작업은 DB 트랜잭션 하나 안에서 실행되고, 안의 조회 · 회전도 같은 트랜잭션에 참여한다")
    void runsOperationsInOneTransaction() {
        Long sessionId = sessionStore.create(1L, "hash-unit-a", NOW.plusDays(7));
        String[] transactionNames = new String[2];

        boolean rotated = sessionStore.withinStoreTransaction(() -> {
            transactionNames[0] = TransactionSynchronizationManager.getCurrentTransactionName();
            sessionStore.findToken("hash-unit-a");
            transactionNames[1] = TransactionSynchronizationManager.getCurrentTransactionName();
            return TransactionSynchronizationManager.isActualTransactionActive()
                    && sessionStore.rotate(sessionId, "hash-unit-a", "hash-unit-b", NOW);
        });

        assertThat(rotated).isTrue();
        assertThat(transactionNames[0]).endsWith("withinStoreTransaction");
        assertThat(transactionNames[1]).isEqualTo(transactionNames[0]);
        assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
        sessionStore.deleteSession(sessionId);
    }
}
