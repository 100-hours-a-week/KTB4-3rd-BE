package com.ktb.moyeota.domain.auth.store;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;

@DataJpaTest
@Import(JpaSignupSessionStore.class)
class JpaSignupSessionStoreTest extends SignupSessionStoreContractTest {

    @Autowired
    private JpaSignupSessionStore signupSessionStore;

    @Override
    protected SignupSessionStore store() {
        return signupSessionStore;
    }
}
