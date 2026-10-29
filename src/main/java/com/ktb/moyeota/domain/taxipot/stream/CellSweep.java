package com.ktb.moyeota.domain.taxipot.stream;

import com.ktb.moyeota.domain.taxipot.matching.BatchMatcher;
import com.ktb.moyeota.domain.taxipot.matching.MatchRequest;
import com.ktb.moyeota.domain.taxipot.matching.MatchResult;
import com.ktb.moyeota.domain.taxipot.matching.MatchingRules;
import com.ktb.moyeota.domain.taxipot.matching.OpenPot;
import com.ktb.moyeota.domain.taxipot.message.MatchRequestData;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;

public class CellSweep {

    private static final Comparator<CellRequest> ARRIVAL = Comparator
            .comparing((CellRequest request) -> request.request().requestedAt())
            .thenComparingLong(CellRequest::requestId);

    private final BatchMatcher matcher;
    private final int capacity;

    public CellSweep(MatchingRules rules, int neighborLimit) {
        this.matcher = new BatchMatcher(rules, neighborLimit);
        this.capacity = rules.capacity();
    }

    public List<MatchResult> sweep(CellView view, LocalDateTime cutoff, CellEntries entries) {
        Map<Long, CellRequest> requests = new HashMap<>();
        view.requests().forEach(request -> requests.put(request.requestId(), request));
        List<CellPot> pots = new ArrayList<>();
        for (CellPot pot : view.pots().stream().sorted(Comparator.comparing(CellPot::potId)).toList()) {
            if (pot.departureAt().isBefore(cutoff)) {
                pot.memberIds().forEach(entries::removeRequest);
                entries.removePot(pot.potId());
            } else {
                pots.add(pot);
            }
        }
        Map<LocalDateTime, List<CellRequest>> waiting = new TreeMap<>();
        for (CellRequest request : view.requests().stream().filter(request -> !request.inPot()).sorted(ARRIVAL)
                .toList()) {
            if (request.request().departureAt().isBefore(cutoff)) {
                entries.removeRequest(request.requestId());
            } else {
                waiting.computeIfAbsent(request.request().departureAt(), key -> new ArrayList<>()).add(request);
            }
        }
        List<MatchResult> results = new ArrayList<>();
        waiting.forEach((departureAt, sameDeparture) -> {
            Map<String, CellPot> openPots = pots.stream()
                    .filter(pot -> pot.departureAt().equals(departureAt) && pot.memberIds().size() < capacity)
                    .collect(Collectors.toMap(CellPot::potKey, pot -> pot, (a, b) -> a, LinkedHashMap::new));
            List<MatchResult> matched = matcher.match(
                    sameDeparture.stream().map(request -> request.request().toMatchRequest()).toList(),
                    openPots.values().stream().map(pot -> toOpenPot(pot, requests)).toList());
            for (MatchResult result : matched) {
                apply(result, openPots, requests, entries);
                results.add(result);
            }
        });
        return results;
    }

    private void apply(
            MatchResult result, Map<String, CellPot> openPots, Map<Long, CellRequest> requests,
            CellEntries entries) {
        List<Long> joined = result.requests().stream().map(MatchRequest::requestId).toList();
        CellPot pot = result.opensPot()
                ? new CellPot(result.potKey(), result.departureAt(), joined)
                : openPots.get(result.potKey()).with(joined);
        openPots.put(result.potKey(), pot);
        entries.put(pot);
        joined.forEach(requestId -> entries.put(requests.get(requestId).joining(pot.potId())));
    }

    private static OpenPot toOpenPot(CellPot pot, Map<Long, CellRequest> requests) {
        return new OpenPot(pot.potKey(), pot.departureAt(), pot.memberIds().stream()
                .map(requests::get)
                .map(CellRequest::request)
                .map(MatchRequestData::toMatchRequest)
                .toList());
    }
}
