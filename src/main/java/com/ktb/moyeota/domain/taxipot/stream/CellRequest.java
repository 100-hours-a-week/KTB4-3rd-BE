package com.ktb.moyeota.domain.taxipot.stream;

import com.ktb.moyeota.domain.taxipot.message.MatchRequestData;
import java.time.Duration;
import java.time.LocalDateTime;

public record CellRequest(
        long requestId, MatchRequestData request, String potId, String resultId, boolean finished,
        LocalDateTime canceledAt) {

    private static final Duration KEPT_WITHOUT_REQUEST = Duration.ofHours(1);

    public static CellRequest waiting(MatchRequestData request) {
        return new CellRequest(request.requestId(), request, null, null, false, null);
    }

    static CellRequest canceledBeforeArrival(long requestId, LocalDateTime canceledAt) {
        return new CellRequest(requestId, null, null, null, true, canceledAt);
    }

    public boolean inPot() {
        return potId != null;
    }

    public boolean matchable() {
        return !finished && !inPot();
    }

    boolean joinedBy(String resultId) {
        return inPot() && resultId.equals(this.resultId);
    }

    boolean expired(LocalDateTime cutoff) {
        LocalDateTime keptUntil = request == null
                ? canceledAt.plus(KEPT_WITHOUT_REQUEST)
                : request.departureAt();
        return keptUntil.isBefore(cutoff);
    }

    CellRequest joining(String potId, String resultId) {
        return new CellRequest(requestId, request, potId, resultId, false, canceledAt);
    }

    CellRequest finish() {
        return new CellRequest(requestId, request, null, null, true, canceledAt);
    }

    CellRequest arrived(MatchRequestData request) {
        return new CellRequest(requestId, request, potId, resultId, finished, canceledAt);
    }
}
