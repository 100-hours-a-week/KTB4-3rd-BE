package com.ktb.moyeota.domain.taxipot.service;

import com.ktb.moyeota.domain.taxipot.message.MatchDecidedData;
import com.ktb.moyeota.domain.taxipot.message.MatchRejectedData;
import com.ktb.moyeota.domain.taxipot.message.MatchRequestData;
import com.ktb.moyeota.domain.taxipot.message.MatchingMessages;
import com.ktb.moyeota.domain.taxipot.model.MatchApplyOutcome;
import com.ktb.moyeota.domain.taxipot.model.MatchApplyOutcome.Rejected;
import com.ktb.moyeota.global.outbox.service.OutboxRecorder;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class MatchDecisionService {

    private final TaxiPotMatchReflector reflector;
    private final OutboxRecorder outboxRecorder;

    @Transactional
    public MatchApplyOutcome apply(String cellKey, MatchDecidedData decided) {
        MatchApplyOutcome outcome = reflector.apply(decided.toMatchResult());
        if (outcome instanceof Rejected rejected) {
            outboxRecorder.record(MatchingMessages.REQUEST_TOPIC, cellKey, MatchingMessages.REJECTED,
                    MatchingMessages.SCHEMA_VERSION, new MatchRejectedData(decided.resultId(), decided.potKey(),
                            decided.requests().stream().map(MatchRequestData::requestId).toList(),
                            rejected.reason()));
        }
        return outcome;
    }
}
