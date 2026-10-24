package com.ktb.moyeota.domain.taxipot.config;

import com.ktb.moyeota.domain.taxipot.matching.RegionGrid;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties(prefix = "moyeota.taxi-pot.matching")
public record TaxiPotMatchingProperties(
        @DefaultValue("instant") MatchingMode mode,
        @DefaultValue("2000") int cellMeters,
        @DefaultValue("100") int radiusMeters,
        @DefaultValue("8") int neighborLimit
) {

    public TaxiPotMatchingProperties {
        if (cellMeters <= 0) {
            throw new IllegalArgumentException("cell-meters는 0보다 커야 합니다");
        }
        if (radiusMeters < 0) {
            throw new IllegalArgumentException("radius-meters는 0 이상이어야 합니다");
        }
        if (neighborLimit <= 0) {
            throw new IllegalArgumentException("neighbor-limit는 0보다 커야 합니다");
        }
    }

    public boolean isBatch() {
        return mode == MatchingMode.BATCH;
    }

    public RegionGrid regionGrid() {
        return new RegionGrid(cellMeters);
    }

    public enum MatchingMode {
        INSTANT,
        BATCH
    }
}
