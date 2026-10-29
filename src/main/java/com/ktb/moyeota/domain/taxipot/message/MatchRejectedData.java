package com.ktb.moyeota.domain.taxipot.message;

import com.ktb.moyeota.domain.taxipot.model.MatchApplyOutcome.RejectReason;
import java.util.List;

public record MatchRejectedData(String resultId, String potKey, List<Long> requestIds, RejectReason reason) {

    public MatchRejectedData {
        requestIds = List.copyOf(requestIds);
    }
}
