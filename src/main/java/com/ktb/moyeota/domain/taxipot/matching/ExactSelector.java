package com.ktb.moyeota.domain.taxipot.matching;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class ExactSelector implements CandidateSelector {

    private static final int DEFAULT_COMPONENT_LIMIT = 22;

    private final int componentLimit;
    private final GreedySelector fallback = new GreedySelector();

    public ExactSelector() {
        this(DEFAULT_COMPONENT_LIMIT);
    }

    ExactSelector(int componentLimit) {
        this.componentLimit = componentLimit;
    }

    @Override
    public List<MatchResult> select(List<MatchCandidate> ranked) {
        Map<String, Integer> elementIndex = new HashMap<>();
        List<int[]> elementsOf = new ArrayList<>();
        for (MatchCandidate candidate : ranked) {
            elementsOf.add(elementsOf(candidate, elementIndex));
        }
        int[] parent = new int[elementIndex.size()];
        Arrays.setAll(parent, index -> index);
        for (int[] elements : elementsOf) {
            for (int element : elements) {
                union(parent, elements[0], element);
            }
        }
        Map<Integer, List<Integer>> candidatesByComponent = new LinkedHashMap<>();
        for (int i = 0; i < ranked.size(); i++) {
            candidatesByComponent.computeIfAbsent(find(parent, elementsOf.get(i)[0]), root -> new ArrayList<>()).add(i);
        }

        List<Integer> chosen = new ArrayList<>();
        for (List<Integer> candidateIndexes : candidatesByComponent.values()) {
            Map<Integer, Integer> local = new LinkedHashMap<>();
            for (int index : candidateIndexes) {
                for (int element : elementsOf.get(index)) {
                    local.putIfAbsent(element, local.size());
                }
            }
            if (local.size() > componentLimit) {
                chosen.addAll(greedyIndexes(ranked, candidateIndexes));
                continue;
            }
            chosen.addAll(new ComponentSolver(ranked, candidateIndexes, elementsOf, local).solve());
        }
        chosen.sort(null);
        return chosen.stream().map(index -> ranked.get(index).result()).toList();
    }

    private int[] elementsOf(MatchCandidate candidate, Map<String, Integer> elementIndex) {
        MatchResult result = candidate.result();
        List<String> keys = new ArrayList<>();
        result.requests().forEach(request -> keys.add("r" + request.requestId()));
        if (!result.opensPot()) {
            keys.add("p" + result.potKey());
        }
        int[] elements = new int[keys.size()];
        for (int i = 0; i < keys.size(); i++) {
            elements[i] = elementIndex.computeIfAbsent(keys.get(i), key -> elementIndex.size());
        }
        return elements;
    }

    private List<Integer> greedyIndexes(List<MatchCandidate> ranked, List<Integer> candidateIndexes) {
        List<String> chosenIds = fallback.select(candidateIndexes.stream().map(ranked::get).toList()).stream()
                .map(MatchResult::resultId)
                .toList();
        return candidateIndexes.stream()
                .filter(index -> chosenIds.contains(ranked.get(index).result().resultId()))
                .toList();
    }

    private static int find(int[] parent, int element) {
        while (parent[element] != element) {
            parent[element] = parent[parent[element]];
            element = parent[element];
        }
        return element;
    }

    private static void union(int[] parent, int left, int right) {
        parent[find(parent, left)] = find(parent, right);
    }

    private static final class ComponentSolver {

        private final List<Integer> candidateIndexes;
        private final int[] masks;
        private final long[] scores;
        private final int full;
        private final long[] bestScore;
        private final int[] bestChoice;
        private final boolean[] solved;

        private ComponentSolver(
                List<MatchCandidate> ranked, List<Integer> candidateIndexes, List<int[]> elementsOf,
                Map<Integer, Integer> local) {
            this.candidateIndexes = candidateIndexes;
            this.masks = new int[candidateIndexes.size()];
            this.scores = new long[candidateIndexes.size()];
            for (int i = 0; i < candidateIndexes.size(); i++) {
                MatchCandidate candidate = ranked.get(candidateIndexes.get(i));
                for (int element : elementsOf.get(candidateIndexes.get(i))) {
                    masks[i] |= 1 << local.get(element);
                }
                scores[i] = candidate.result().requests().size() * 1_000_000_000L
                        - Math.round(candidate.spreadMeters() * 1_000);
            }
            this.full = (1 << local.size()) - 1;
            this.bestScore = new long[full + 1];
            this.bestChoice = new int[full + 1];
            this.solved = new boolean[full + 1];
        }

        private List<Integer> solve() {
            List<Integer> chosen = new ArrayList<>();
            int mask = 0;
            while (mask != full) {
                best(mask);
                int choice = bestChoice[mask];
                if (choice < 0) {
                    mask |= Integer.lowestOneBit(~mask & full);
                    continue;
                }
                chosen.add(candidateIndexes.get(choice));
                mask |= masks[choice];
            }
            return chosen;
        }

        private long best(int mask) {
            if (mask == full) {
                return 0;
            }
            if (solved[mask]) {
                return bestScore[mask];
            }
            int lowest = Integer.lowestOneBit(~mask & full);
            long bestValue = best(mask | lowest);
            int choice = -1;
            for (int i = 0; i < masks.length; i++) {
                if ((masks[i] & lowest) == 0 || (masks[i] & mask) != 0) {
                    continue;
                }
                long value = scores[i] + best(mask | masks[i]);
                if (value > bestValue) {
                    bestValue = value;
                    choice = i;
                }
            }
            solved[mask] = true;
            bestScore[mask] = bestValue;
            bestChoice[mask] = choice;
            return bestValue;
        }
    }
}
