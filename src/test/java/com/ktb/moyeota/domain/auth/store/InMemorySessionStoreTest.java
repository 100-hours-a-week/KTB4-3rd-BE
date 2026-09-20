package com.ktb.moyeota.domain.auth.store;

import com.ktb.moyeota.domain.auth.service.InMemorySessionStore;

class InMemorySessionStoreTest extends SessionStoreContractTest {

    private final SessionStore store = new InMemorySessionStore();

    @Override
    protected SessionStore store() {
        return store;
    }
}
