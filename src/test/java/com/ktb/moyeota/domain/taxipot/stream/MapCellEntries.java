package com.ktb.moyeota.domain.taxipot.stream;

import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;

class MapCellEntries implements CellEntries {

    final Map<Long, CellRequest> requests = new TreeMap<>();
    final Map<String, CellPot> pots = new TreeMap<>();

    @Override
    public Optional<CellRequest> request(long requestId) {
        return Optional.ofNullable(requests.get(requestId));
    }

    @Override
    public Optional<CellPot> pot(String potId) {
        return Optional.ofNullable(pots.get(potId));
    }

    @Override
    public void put(CellRequest request) {
        requests.put(request.requestId(), request);
    }

    @Override
    public void removeRequest(long requestId) {
        requests.remove(requestId);
    }

    @Override
    public void put(CellPot pot) {
        pots.put(pot.potId(), pot);
    }

    @Override
    public void removePot(String potId) {
        pots.remove(potId);
    }

    CellView view() {
        return new CellView(requests.values().stream().toList(), pots.values().stream().toList());
    }

    boolean isEmpty() {
        return requests.isEmpty() && pots.isEmpty();
    }
}
