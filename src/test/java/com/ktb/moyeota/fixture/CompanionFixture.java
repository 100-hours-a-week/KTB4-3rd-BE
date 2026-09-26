package com.ktb.moyeota.fixture;

import com.ktb.moyeota.domain.companion.entity.Companion;
import com.ktb.moyeota.domain.companion.entity.CompanionKind;
import com.ktb.moyeota.domain.companion.entity.CompanionStatus;
import com.ktb.moyeota.domain.companion.entity.TransportType;
import com.ktb.moyeota.domain.user.entity.User;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import org.springframework.test.util.ReflectionTestUtils;

public final class CompanionFixture {

    public static final String ORIGIN_NAME = "판교역";
    public static final BigDecimal ORIGIN_LAT = new BigDecimal("37.394500");
    public static final BigDecimal ORIGIN_LNG = new BigDecimal("127.111200");
    public static final String DEST_NAME = "강남역";
    public static final BigDecimal DEST_LAT = new BigDecimal("37.497900");
    public static final BigDecimal DEST_LNG = new BigDecimal("127.027600");
    public static final LocalDateTime DEPARTURE_AT = LocalDateTime.of(2026, 9, 5, 8, 30);

    private CompanionFixture() {
    }

    public static Companion taxiPot(User host, CompanionStatus status) {
        Companion taxiPot = recruiting(host, CompanionKind.TAXI_POT, TransportType.TAXI, 4);
        ReflectionTestUtils.setField(taxiPot, "status", status);
        return taxiPot;
    }

    public static Companion taxiPot(User host, CompanionStatus status, int currentCount) {
        Companion taxiPot = taxiPot(host, status);
        ReflectionTestUtils.setField(taxiPot, "currentCount", currentCount);
        return taxiPot;
    }

    public static Companion companionPost(User host) {
        return recruiting(host, CompanionKind.COMPANION, TransportType.SUBWAY, 10);
    }

    private static Companion recruiting(
            User host, CompanionKind kind, TransportType transportType, int capacity) {
        return Companion.createCompanionPost(host, kind, transportType, null,
                ORIGIN_NAME, ORIGIN_LAT, ORIGIN_LNG, DEST_NAME, DEST_LAT, DEST_LNG, DEPARTURE_AT, capacity);
    }
}
