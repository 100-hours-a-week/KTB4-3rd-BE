package com.ktb.moyeota.domain.notification.service;

public enum PushDeliveryOutcome {

    NOT_CLAIMED,

    SENT,

    SKIPPED,

    FAILED,

    RETRY_SCHEDULED,

    RETRIES_EXHAUSTED;

    public boolean retryable() {
        return this == RETRY_SCHEDULED || this == RETRIES_EXHAUSTED;
    }
}
