package com.ktb.moyeota.domain.auth.store;

class InMemorySignupSessionStoreTest extends SignupSessionStoreContractTest {

    private final SignupSessionStore store = new InMemorySignupSessionStore();

    @Override
    protected SignupSessionStore store() {
        return store;
    }
}
