package com.ktb.moyeota.domain.taxipot.repository;

import com.ktb.moyeota.domain.taxipot.matching.RegionCell;

public record MatchingRegion(Long row, Long col) {

    public RegionCell toCell() {
        return new RegionCell(row, col);
    }
}
