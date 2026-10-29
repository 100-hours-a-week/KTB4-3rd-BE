package com.ktb.moyeota.domain.taxipot.stream;

import com.ktb.moyeota.domain.taxipot.message.MatchRequestData;

public record CellRequest(MatchRequestData request, String potId) {

    public static CellRequest waiting(MatchRequestData request) {
        return new CellRequest(request, null);
    }

    public long requestId() {
        return request.requestId();
    }

    public boolean inPot() {
        return potId != null;
    }

    CellRequest joining(String potId) {
        return new CellRequest(request, potId);
    }
}
