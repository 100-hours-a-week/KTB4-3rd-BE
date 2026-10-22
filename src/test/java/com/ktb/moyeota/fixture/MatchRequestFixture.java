package com.ktb.moyeota.fixture;

import com.ktb.moyeota.domain.taxipot.matching.GeoPoint;
import com.ktb.moyeota.domain.taxipot.matching.MatchRequest;
import com.ktb.moyeota.domain.taxipot.matching.Place;
import java.math.BigDecimal;
import java.time.LocalDateTime;

public final class MatchRequestFixture {

    public static final LocalDateTime DEPARTURE_AT = LocalDateTime.of(2026, 10, 20, 9, 0);
    public static final LocalDateTime REQUESTED_AT = LocalDateTime.of(2026, 10, 20, 8, 30);
    public static final Place ORIGIN = new Place("판교역 신분당선", GeoPoint.of(new BigDecimal("37.394726"), new BigDecimal("127.111209")));
    public static final Place DEST = new Place("강남역 2호선", GeoPoint.of(new BigDecimal("37.498086"), new BigDecimal("127.028001")));

    private MatchRequestFixture() {
    }

    public static MatchRequest matchRequest(long requestId) {
        return new MatchRequest(requestId, requestId, ORIGIN, DEST, DEPARTURE_AT, REQUESTED_AT);
    }
}
