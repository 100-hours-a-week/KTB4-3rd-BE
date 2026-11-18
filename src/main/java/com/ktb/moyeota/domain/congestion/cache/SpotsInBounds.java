package com.ktb.moyeota.domain.congestion.cache;

import java.util.List;

public sealed interface SpotsInBounds {

    record Found(List<CachedSpot> spots) implements SpotsInBounds {

        public Found {
            spots = List.copyOf(spots);
        }
    }

    record NotIndexed() implements SpotsInBounds {
    }

    record Unavailable() implements SpotsInBounds {
    }
}
