package com.ktb.moyeota.domain.taxipot.model;

public sealed interface MatchApplyOutcome {

    record Applied(Long taxiPotId) implements MatchApplyOutcome {
    }

    record AlreadyApplied(Long taxiPotId) implements MatchApplyOutcome {
    }

    record Rejected(RejectReason reason) implements MatchApplyOutcome {
    }

    enum RejectReason {
        REQUEST_NOT_FOUND,
        REQUEST_NOT_MATCHING,
        POT_NOT_FOUND,
        POT_NOT_RECRUITING,
        POT_FULL,
        DEPARTED
    }
}
