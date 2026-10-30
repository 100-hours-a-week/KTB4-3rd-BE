package com.ktb.moyeota.domain.taxipot.stream;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.function.BiConsumer;
import org.apache.kafka.streams.KeyValue;
import org.apache.kafka.streams.state.KeyValueIterator;
import org.apache.kafka.streams.state.KeyValueStore;

class StoreCellEntries implements CellEntries {

    private static final String SEPARATOR = "|";

    private final KeyValueStore<String, CellRequest> requests;
    private final KeyValueStore<String, CellPot> pots;
    private final String cell;

    StoreCellEntries(KeyValueStore<String, CellRequest> requests, KeyValueStore<String, CellPot> pots, String cell) {
        this.requests = requests;
        this.pots = pots;
        this.cell = cell;
    }

    static Map<String, CellView> views(
            KeyValueStore<String, CellRequest> requests, KeyValueStore<String, CellPot> pots) {
        Map<String, List<CellRequest>> requestsByCell = new TreeMap<>();
        Map<String, List<CellPot>> potsByCell = new TreeMap<>();
        each(requests, (cell, request) -> requestsByCell.computeIfAbsent(cell, key -> new ArrayList<>()).add(request));
        each(pots, (cell, pot) -> potsByCell.computeIfAbsent(cell, key -> new ArrayList<>()).add(pot));
        Set<String> cells = new TreeSet<>(requestsByCell.keySet());
        cells.addAll(potsByCell.keySet());
        Map<String, CellView> views = new LinkedHashMap<>();
        cells.forEach(cell -> views.put(cell, new CellView(
                requestsByCell.getOrDefault(cell, List.of()), potsByCell.getOrDefault(cell, List.of()))));
        return views;
    }

    @Override
    public Optional<CellRequest> request(long requestId) {
        return Optional.ofNullable(requests.get(key(requestId)));
    }

    @Override
    public Optional<CellPot> pot(String potId) {
        return Optional.ofNullable(pots.get(key(potId)));
    }

    @Override
    public void put(CellRequest request) {
        requests.put(key(request.requestId()), request);
    }

    @Override
    public void removeRequest(long requestId) {
        requests.delete(key(requestId));
    }

    @Override
    public void put(CellPot pot) {
        pots.put(key(pot.potId()), pot);
    }

    @Override
    public void removePot(String potId) {
        pots.delete(key(potId));
    }

    private String key(Object id) {
        return cell + SEPARATOR + id;
    }

    private static <V> void each(KeyValueStore<String, V> store, BiConsumer<String, V> action) {
        try (KeyValueIterator<String, V> all = store.all()) {
            all.forEachRemaining((KeyValue<String, V> entry) ->
                    action.accept(entry.key.substring(0, entry.key.indexOf(SEPARATOR)), entry.value));
        }
    }
}
