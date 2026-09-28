package com.ktb.moyeota.domain.auth.store;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;

@DataJpaTest
@Import(JpaSessionStore.class)
class JpaSessionStoreTest extends SessionStoreContractTest {

    @Autowired
    private JpaSessionStore sessionStore;

    @Override
    protected SessionStore store() {
        return sessionStore;
    }
}
