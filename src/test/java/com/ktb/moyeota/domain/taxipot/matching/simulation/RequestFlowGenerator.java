package com.ktb.moyeota.domain.taxipot.matching.simulation;

import com.ktb.moyeota.domain.taxipot.matching.GeoDistance;
import com.ktb.moyeota.domain.taxipot.matching.MatchRequest;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

public class RequestFlowGenerator {

    private static final long POPULARITY_SEED = 20_261_015L;
    private static final int MIN_LEAD_MINUTES = 10;
    private static final int MAX_LEAD_MINUTES = 60;
    private static final int DEPARTURE_SLOT_MINUTES = 30;
    private static final double VARIANT_RADIUS_METERS = 50;
    private static final long NEIGHBOR_LAT_CELL_MICROS = 500;
    private static final long NEIGHBOR_LNG_CELL_MICROS = 700;

    private final List<SimPlace> places;
    private final double popularityExponent;
    private final Map<String, SpotPool> pools = new HashMap<>();
    private final Map<Cell, List<SimPlace>> neighborIndex = new HashMap<>();
    private final Map<String, List<SimPlace>> neighborCache = new HashMap<>();

    public RequestFlowGenerator(List<SimPlace> places, double popularityExponent) {
        this.places = places.stream().sorted(Comparator.comparing(SimPlace::id)).toList();
        this.popularityExponent = popularityExponent;
        for (SimPlace place : this.places) {
            neighborIndex.computeIfAbsent(Cell.of(place), cell -> new ArrayList<>()).add(place);
        }
    }

    public List<GeneratedRequest> generate(
            Scenario scenario, LocalDate day, int requests, double variantProbability, long seed) {
        Random random = new Random(seed);
        LocalDateTime from = LocalDateTime.of(day, scenario.requestFrom());
        long windowSeconds = Duration.between(scenario.requestFrom(), scenario.requestTo()).toSeconds();
        List<LocalDateTime> requestTimes = new ArrayList<>();
        for (int i = 0; i < requests; i++) {
            requestTimes.add(from.plusSeconds(random.nextLong(windowSeconds)));
        }
        Collections.sort(requestTimes);

        SpotPool originPool = pool(scenario.origin());
        SpotPool destPool = pool(scenario.dest());
        List<GeneratedRequest> flow = new ArrayList<>();
        long requestId = 1;
        for (LocalDateTime requestedAt : requestTimes) {
            LocalDateTime departureAt = departureSlot(requestedAt.plusMinutes(
                    MIN_LEAD_MINUTES + random.nextInt(MAX_LEAD_MINUTES - MIN_LEAD_MINUTES + 1)));
            SimPlace origin = pickVariant(originPool.pick(random), variantProbability, random);
            SimPlace dest = pickVariant(destPool.pick(random), variantProbability, random);
            MatchRequest request = new MatchRequest(
                    requestId, requestId, origin.toPlace(), dest.toPlace(), departureAt, requestedAt);
            flow.add(new GeneratedRequest(request, origin, dest));
            requestId++;
        }
        return flow;
    }

    private SpotPool pool(Spot spot) {
        return pools.computeIfAbsent(spot.name(), name -> SpotPool.of(spot, places, popularityExponent));
    }

    private LocalDateTime departureSlot(LocalDateTime earliest) {
        LocalDateTime slot = earliest.truncatedTo(ChronoUnit.HOURS)
                .plusMinutes(earliest.getMinute() / DEPARTURE_SLOT_MINUTES * DEPARTURE_SLOT_MINUTES);
        return slot.isBefore(earliest) ? slot.plusMinutes(DEPARTURE_SLOT_MINUTES) : slot;
    }

    private SimPlace pickVariant(SimPlace chosen, double variantProbability, Random random) {
        boolean pickOther = random.nextDouble() < variantProbability;
        int index = random.nextInt(Integer.MAX_VALUE);
        if (!pickOther) {
            return chosen;
        }
        List<SimPlace> neighbors = neighborCache.computeIfAbsent(chosen.id(), id -> neighborsOf(chosen));
        return neighbors.isEmpty() ? chosen : neighbors.get(index % neighbors.size());
    }

    private List<SimPlace> neighborsOf(SimPlace center) {
        Cell cell = Cell.of(center);
        List<SimPlace> neighbors = new ArrayList<>();
        for (long row = cell.row() - 1; row <= cell.row() + 1; row++) {
            for (long col = cell.col() - 1; col <= cell.col() + 1; col++) {
                for (SimPlace place : neighborIndex.getOrDefault(new Cell(row, col), List.of())) {
                    if (!place.id().equals(center.id())
                            && GeoDistance.meters(center.point(), place.point()) <= VARIANT_RADIUS_METERS) {
                        neighbors.add(place);
                    }
                }
            }
        }
        neighbors.sort(Comparator.comparing(SimPlace::id));
        return neighbors;
    }

    private record Cell(long row, long col) {

        static Cell of(SimPlace place) {
            return new Cell(
                    Math.floorDiv(place.point().latMicros(), NEIGHBOR_LAT_CELL_MICROS),
                    Math.floorDiv(place.point().lngMicros(), NEIGHBOR_LNG_CELL_MICROS));
        }
    }

    private record SpotPool(List<PlaceGroup> groups, double[] groupCumulative, Map<PlaceGroup, ZipfPlaces> byGroup) {

        static SpotPool of(Spot spot, List<SimPlace> places, double exponent) {
            Map<PlaceGroup, List<SimPlace>> inside = new EnumMap<>(PlaceGroup.class);
            for (SimPlace place : places) {
                if (GeoDistance.meters(spot.center(), place.point()) <= spot.radiusMeters()) {
                    inside.computeIfAbsent(PlaceGroup.of(place), group -> new ArrayList<>()).add(place);
                }
            }
            List<PlaceGroup> groups = new ArrayList<>();
            List<Double> cumulative = new ArrayList<>();
            Map<PlaceGroup, ZipfPlaces> byGroup = new EnumMap<>(PlaceGroup.class);
            double sum = 0;
            for (PlaceGroup group : PlaceGroup.values()) {
                double share = spot.shares().getOrDefault(group, 0.0);
                if (share <= 0 || !inside.containsKey(group)) {
                    continue;
                }
                Random popularity = new Random(POPULARITY_SEED + spot.name().hashCode() * 31L + group.ordinal());
                byGroup.put(group, ZipfPlaces.of(inside.get(group), popularity, exponent));
                sum += share;
                groups.add(group);
                cumulative.add(sum);
            }
            if (groups.isEmpty()) {
                throw new IllegalArgumentException(spot.name() + " 안에 고를 장소가 없습니다");
            }
            return new SpotPool(groups, cumulative.stream().mapToDouble(Double::doubleValue).toArray(), byGroup);
        }

        SimPlace pick(Random random) {
            double target = random.nextDouble() * groupCumulative[groupCumulative.length - 1];
            int index = Arrays.binarySearch(groupCumulative, target);
            PlaceGroup group = groups.get(index >= 0 ? index : -index - 1);
            return byGroup.get(group).pick(random);
        }
    }

    private record ZipfPlaces(List<SimPlace> ranked, double[] cumulative) {

        static ZipfPlaces of(List<SimPlace> places, Random popularity, double exponent) {
            List<SimPlace> ranked = new ArrayList<>(places);
            Collections.shuffle(ranked, popularity);
            double[] cumulative = new double[ranked.size()];
            double sum = 0;
            for (int rank = 0; rank < ranked.size(); rank++) {
                sum += 1 / Math.pow(rank + 1, exponent);
                cumulative[rank] = sum;
            }
            return new ZipfPlaces(ranked, cumulative);
        }

        SimPlace pick(Random random) {
            double target = random.nextDouble() * cumulative[cumulative.length - 1];
            int index = Arrays.binarySearch(cumulative, target);
            return ranked.get(index >= 0 ? index : -index - 1);
        }
    }
}
