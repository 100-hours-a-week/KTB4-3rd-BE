package com.ktb.moyeota.domain.taxipot.message;

import com.ktb.moyeota.domain.taxipot.matching.RegionCell;

public final class MatchingMessages {

    public static final String REQUEST_TOPIC = "matching.request";
    public static final String DECISION_TOPIC = "matching.decision";
    public static final int SCHEMA_VERSION = 1;

    public static final String REQUESTED = "matching.requested";
    public static final String CANCELED = "matching.canceled";
    public static final String LEFT = "matching.left";
    public static final String RIDE_STARTED = "matching.ride-started";
    public static final String REJECTED = "matching.rejected";
    public static final String DECIDED = "matching.decided";

    private static final String KEY_SEPARATOR = ":";

    private MatchingMessages() {
    }

    public static String key(RegionCell region) {
        return region.row() + KEY_SEPARATOR + region.col();
    }
}
