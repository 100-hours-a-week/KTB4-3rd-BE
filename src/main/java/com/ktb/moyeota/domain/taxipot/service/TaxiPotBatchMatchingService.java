package com.ktb.moyeota.domain.taxipot.service;

import com.ktb.moyeota.domain.companion.entity.Companion;
import com.ktb.moyeota.domain.taxipot.config.TaxiPotMatchingProperties;
import com.ktb.moyeota.domain.taxipot.entity.TaxiPotMatchRequest;
import com.ktb.moyeota.domain.taxipot.matching.BatchMatcher;
import com.ktb.moyeota.domain.taxipot.matching.GeoPoint;
import com.ktb.moyeota.domain.taxipot.matching.MatchRequest;
import com.ktb.moyeota.domain.taxipot.matching.MatchResult;
import com.ktb.moyeota.domain.taxipot.matching.MatchingRules;
import com.ktb.moyeota.domain.taxipot.matching.OpenPot;
import com.ktb.moyeota.domain.taxipot.matching.Place;
import com.ktb.moyeota.domain.taxipot.matching.RegionCell;
import com.ktb.moyeota.domain.taxipot.model.MatchApplyOutcome.AlreadyApplied;
import com.ktb.moyeota.domain.taxipot.model.MatchApplyOutcome.Applied;
import com.ktb.moyeota.domain.taxipot.model.MatchApplyOutcome.Rejected;
import com.ktb.moyeota.domain.taxipot.repository.MatchingRegion;
import com.ktb.moyeota.domain.taxipot.repository.TaxiPotMatchRequestRepository;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

@Slf4j
@Service
@RequiredArgsConstructor
@EnableConfigurationProperties(TaxiPotMatchingProperties.class)
public class TaxiPotBatchMatchingService {

    private static final int TAXI_POT_CAPACITY = 4;

    private final TaxiPotMatchRequestRepository taxiPotMatchRequestRepository;
    private final TaxiPotMatchReflector reflector;
    private final TaxiPotMatchingProperties matchingProperties;
    private final TransactionTemplate transactionTemplate;
    private final Clock clock;

    public BatchMatchingSummary runOnce() {
        LocalDateTime now = LocalDateTime.now(clock);
        LocalDateTime cutoff = now.truncatedTo(ChronoUnit.MINUTES);
        Integer failed = transactionTemplate.execute(status -> taxiPotMatchRequestRepository.failDeparted(cutoff, now));

        BatchMatcher matcher = new BatchMatcher(
                new MatchingRules(matchingProperties.radiusMeters(), TAXI_POT_CAPACITY),
                matchingProperties.neighborLimit());
        Tally tally = new Tally();
        List<RegionCell> regions = findTargetRegions();
        for (RegionCell region : regions) {
            try {
                for (MatchBatch batch : loadBatches(region, cutoff)) {
                    matcher.match(batch.waiting(), batch.openPots()).forEach(result -> apply(result, tally));
                }
            } catch (RuntimeException e) {
                tally.errors++;
                log.warn("[BATCH_MATCHING_REGION_FAILED] region={}", region, e);
            }
        }

        BatchMatchingSummary summary = tally.toSummary(failed == null ? 0 : failed, regions.size());
        if (summary.hasWork()) {
            log.info("[BATCH_MATCHING] {}", summary);
        }
        return summary;
    }

    private List<RegionCell> findTargetRegions() {
        return taxiPotMatchRequestRepository.findMatchingRegions().stream()
                .map(MatchingRegion::toCell)
                .toList();
    }

    private List<MatchBatch> loadBatches(RegionCell region, LocalDateTime cutoff) {
        return transactionTemplate.execute(status -> {
            Map<LocalDateTime, List<MatchRequest>> waiting = taxiPotMatchRequestRepository
                    .findMatchingInRegion(region.row(), region.col()).stream()
                    .collect(Collectors.groupingBy(TaxiPotMatchRequest::getDepartureAt, LinkedHashMap::new,
                            Collectors.mapping(TaxiPotBatchMatchingService::toMatchRequest, Collectors.toList())));
            Map<LocalDateTime, List<OpenPot>> openPots = toOpenPots(taxiPotMatchRequestRepository
                    .findOpenPotMembersInRegion(region.row(), region.col(), cutoff));

            List<MatchBatch> batches = new ArrayList<>();
            waiting.forEach((departureAt, requests) ->
                    batches.add(new MatchBatch(requests, openPots.getOrDefault(departureAt, List.of()))));
            return batches;
        });
    }

    private static Map<LocalDateTime, List<OpenPot>> toOpenPots(List<TaxiPotMatchRequest> members) {
        Map<Long, List<TaxiPotMatchRequest>> byPot = members.stream()
                .collect(Collectors.groupingBy(member -> member.getCompanion().getId(), LinkedHashMap::new,
                        Collectors.toList()));
        Map<LocalDateTime, List<OpenPot>> openPots = new LinkedHashMap<>();
        byPot.values().forEach(potMembers -> {
            Companion pot = potMembers.getFirst().getCompanion();
            openPots.computeIfAbsent(pot.getDepartureAt(), departureAt -> new ArrayList<>())
                    .add(new OpenPot(potKey(potMembers), pot.getDepartureAt(),
                            potMembers.stream().map(TaxiPotBatchMatchingService::toMatchRequest).toList()));
        });
        return openPots;
    }

    private static String potKey(List<TaxiPotMatchRequest> members) {
        return members.stream()
                .mapToLong(TaxiPotMatchRequest::getId)
                .sorted()
                .mapToObj(Long::toString)
                .collect(Collectors.joining(","));
    }

    private static MatchRequest toMatchRequest(TaxiPotMatchRequest request) {
        return new MatchRequest(
                request.getId(),
                request.getUser().getId(),
                new Place(request.getOriginName(), GeoPoint.of(request.getOriginLat(), request.getOriginLng())),
                new Place(request.getDestName(), GeoPoint.of(request.getDestLat(), request.getDestLng())),
                request.getDepartureAt(),
                request.getCreatedAt());
    }

    private void apply(MatchResult result, Tally tally) {
        try {
            switch (reflector.apply(result)) {
                case Applied _ -> tally.applied++;
                case AlreadyApplied _ -> tally.alreadyApplied++;
                case Rejected _ -> tally.rejected++;
            }
        } catch (RuntimeException e) {
            tally.errors++;
            log.warn("[BATCH_MATCHING_APPLY_FAILED] resultId={}", result.resultId(), e);
        }
    }

    public record BatchMatchingSummary(
            int failed, int regions, int applied, int alreadyApplied, int rejected, int errors) {

        public boolean hasWork() {
            return failed + applied + alreadyApplied + rejected + errors > 0;
        }
    }

    private record MatchBatch(List<MatchRequest> waiting, List<OpenPot> openPots) {
    }

    private static final class Tally {

        private int applied;
        private int alreadyApplied;
        private int rejected;
        private int errors;

        private BatchMatchingSummary toSummary(int failed, int regions) {
            return new BatchMatchingSummary(failed, regions, applied, alreadyApplied, rejected, errors);
        }
    }
}
