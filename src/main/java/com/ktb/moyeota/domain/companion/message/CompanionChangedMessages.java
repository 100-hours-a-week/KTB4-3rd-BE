package com.ktb.moyeota.domain.companion.message;

public final class CompanionChangedMessages {

    public static final String TOPIC = "companion.changed";
    public static final int SCHEMA_VERSION = 1;

    public static final String MATCHED = "companion.matched";
    public static final String PARTICIPANT_JOINED = "companion.participant.joined";
    public static final String PARTICIPANT_LEFT = "companion.participant.left";
    public static final String RIDE_STARTED = "companion.ride.started";
    public static final String RIDE_ENDED = "companion.ride.ended";

    private CompanionChangedMessages() {
    }

    public static String key(Long companionId) {
        return String.valueOf(companionId);
    }
}
