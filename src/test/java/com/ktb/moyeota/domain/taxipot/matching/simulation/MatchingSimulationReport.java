package com.ktb.moyeota.domain.taxipot.matching.simulation;

import com.ktb.moyeota.domain.taxipot.matching.MatchRequest;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

@EnabledIfEnvironmentVariable(named = "MATCHING_SIM_REPORT", matches = "true")
class MatchingSimulationReport {

    private static final LocalDate DAY = LocalDate.of(2026, 10, 20);
    private static final int[] REQUESTS = {100, 500, 2_000};
    private static final double[] CONCENTRATIONS = {1.0, 1.5, 2.0};
    private static final double[] VARIANT_PROBABILITIES = {0, 0.3, 0.6};
    private static final int[] RADII = {50, 100};
    private static final int SEEDS = 5;
    private static final int NEIGHBOR_LIMIT = 8;

    @Test
    void writeResults() throws Exception {
        Path places = Path.of(System.getenv().getOrDefault("MATCHING_SIM_PLACES", "docs/matching-sim/places.json"));
        Path out = Path.of(System.getenv().getOrDefault("MATCHING_SIM_OUT", "docs/benchmark/matching-sim/results.csv"));
        List<SimPlace> placeList = SimPlace.loadAll(places);

        List<Future<List<String>>> futures = new ArrayList<>();
        try (ExecutorService executor = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors())) {
            for (Scenario scenario : CommuteScenarios.all()) {
                for (int requests : REQUESTS) {
                    for (double concentration : CONCENTRATIONS) {
                        for (double variant : VARIANT_PROBABILITIES) {
                            futures.add(executor.submit(
                                    () -> runCase(placeList, scenario, requests, concentration, variant)));
                        }
                    }
                }
            }
            List<String> lines = new ArrayList<>();
            lines.add("scenario,requests,concentration,variant,seed,radius,method,matched,pots,average_pot_size,"
                    + "alone_ratio,median_wait_seconds,p95_wait_seconds,elapsed_ms");
            for (Future<List<String>> future : futures) {
                lines.addAll(future.get());
            }
            Files.createDirectories(out.getParent());
            Files.write(out, lines);
        }
    }

    private List<MatchingMethod> methods() {
        List<MatchingMethod> methods = new ArrayList<>();
        methods.add(MatchingMethod.exact());
        for (int radius : RADII) {
            methods.add(MatchingMethod.instant(radius));
            methods.add(MatchingMethod.batch(5, 2_000, NEIGHBOR_LIMIT, radius));
            methods.add(MatchingMethod.batch(10, 2_000, NEIGHBOR_LIMIT, radius));
            methods.add(MatchingMethod.batch(30, 2_000, NEIGHBOR_LIMIT, radius));
            methods.add(MatchingMethod.batch(30, 1_000, NEIGHBOR_LIMIT, radius));
            methods.add(MatchingMethod.batch(30, 0, NEIGHBOR_LIMIT, radius));
        }
        return methods;
    }

    private List<String> runCase(
            List<SimPlace> places, Scenario scenario, int requests, double concentration, double variant) {
        RequestFlowGenerator generator = new RequestFlowGenerator(places, concentration);
        MatchingSimulator simulator = new MatchingSimulator();
        List<String> lines = new ArrayList<>();
        for (int seed = 1; seed <= SEEDS; seed++) {
            List<MatchRequest> flow = generator.generate(scenario, DAY, requests, variant, seed).stream()
                    .map(GeneratedRequest::request)
                    .toList();
            for (MatchingMethod method : methods()) {
                long startedAt = System.nanoTime();
                SimulationResult result = simulator.run(flow, method);
                long elapsedMs = (System.nanoTime() - startedAt) / 1_000_000;
                lines.add(String.format(Locale.ROOT, "%s,%d,%.1f,%.1f,%d,%d,%s,%d,%d,%.4f,%.4f,%d,%d,%d",
                        scenario.name(), requests, concentration, variant, seed, method.rules().radiusMeters(),
                        method.name(), result.matchedRequests(), result.pots(), result.averagePotSize(),
                        result.aloneRatio(), result.medianWait().toSeconds(), result.p95Wait().toSeconds(),
                        elapsedMs));
            }
        }
        return lines;
    }
}
