package com.ktb.moyeota.domain.taxipot.stream;

import com.ktb.moyeota.domain.taxipot.message.MatchCanceledData;
import com.ktb.moyeota.domain.taxipot.message.MatchLeftData;
import com.ktb.moyeota.domain.taxipot.message.MatchRejectedData;
import com.ktb.moyeota.domain.taxipot.message.MatchRequestData;
import com.ktb.moyeota.domain.taxipot.message.MatchRideStartedData;
import com.ktb.moyeota.domain.taxipot.message.MatchingMessages;
import com.ktb.moyeota.domain.taxipot.model.MatchApplyOutcome.RejectReason;
import com.ktb.moyeota.global.event.EventEnvelope;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

public class CellEvents {

    private static final Set<RejectReason> UNJOINABLE_POT = Set.of(
            RejectReason.POT_NOT_RECRUITING, RejectReason.POT_FULL, RejectReason.DEPARTED);

    private final JsonMapper jsonMapper;

    public CellEvents(JsonMapper jsonMapper) {
        this.jsonMapper = jsonMapper;
    }

    public boolean apply(EventEnvelope<JsonNode> envelope, CellEntries entries) {
        switch (envelope.eventType()) {
            case MatchingMessages.REQUESTED -> request(read(envelope, MatchRequestData.class), entries);
            case MatchingMessages.CANCELED -> cancel(read(envelope, MatchCanceledData.class).requestId(), entries);
            case MatchingMessages.LEFT -> leave(read(envelope, MatchLeftData.class).requestId(), entries);
            case MatchingMessages.RIDE_STARTED ->
                    startRide(read(envelope, MatchRideStartedData.class).requestIds(), entries);
            case MatchingMessages.REJECTED -> reject(read(envelope, MatchRejectedData.class), entries);
            default -> {
                return false;
            }
        }
        return true;
    }

    private void request(MatchRequestData request, CellEntries entries) {
        if (entries.request(request.requestId()).isEmpty()) {
            entries.put(CellRequest.waiting(request));
        }
    }

    private void cancel(long requestId, CellEntries entries) {
        entries.request(requestId).ifPresent(request -> {
            entries.removeRequest(requestId);
            if (request.inPot()) {
                removeMember(request.potId(), requestId, entries);
            }
        });
    }

    private void leave(long requestId, CellEntries entries) {
        entries.request(requestId).filter(CellRequest::inPot).ifPresent(request -> {
            entries.removeRequest(requestId);
            removeMember(request.potId(), requestId, entries);
        });
    }

    private void startRide(Collection<Long> requestIds, CellEntries entries) {
        dropPotsOf(members(requestIds, entries), entries);
    }

    private void reject(MatchRejectedData rejected, CellEntries entries) {
        List<CellRequest> returned = members(rejected.requestIds(), entries);
        if (UNJOINABLE_POT.contains(rejected.reason())) {
            dropPotsOf(returned, entries);
        } else {
            returned.forEach(request -> removeMember(request.potId(), request.requestId(), entries));
        }
        returned.forEach(request -> entries.put(CellRequest.waiting(request.request())));
    }

    private List<CellRequest> members(Collection<Long> requestIds, CellEntries entries) {
        return requestIds.stream()
                .map(entries::request)
                .flatMap(Optional::stream)
                .filter(CellRequest::inPot)
                .toList();
    }

    private void removeMember(String potId, long requestId, CellEntries entries) {
        entries.pot(potId).map(pot -> pot.without(requestId)).ifPresent(pot -> {
            if (pot.memberIds().isEmpty()) {
                entries.removePot(potId);
            } else {
                entries.put(pot);
            }
        });
    }

    private void dropPotsOf(List<CellRequest> members, CellEntries entries) {
        members.stream()
                .map(CellRequest::potId)
                .distinct()
                .forEach(potId -> dropPot(potId, entries));
    }

    private void dropPot(String potId, CellEntries entries) {
        entries.pot(potId).ifPresent(pot -> {
            pot.memberIds().forEach(entries::removeRequest);
            entries.removePot(potId);
        });
    }

    private <T> T read(EventEnvelope<JsonNode> envelope, Class<T> type) {
        return jsonMapper.treeToValue(envelope.data(), type);
    }
}
