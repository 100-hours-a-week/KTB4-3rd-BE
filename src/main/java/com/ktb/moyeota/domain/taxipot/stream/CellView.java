package com.ktb.moyeota.domain.taxipot.stream;

import java.util.List;

public record CellView(List<CellRequest> requests, List<CellPot> pots) {

    public CellView {
        requests = List.copyOf(requests);
        pots = List.copyOf(pots);
    }
}
