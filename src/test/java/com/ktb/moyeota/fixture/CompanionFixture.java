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

    public static final LocalDateTime DEPARTURE_AT = LocalDateTime.of(2026, 9, 5, 8, 30);

    private CompanionFixture() {
    }

    public static Companion taxiPot(User host, CompanionStatus status) {
        Companion taxiPot = recruiting(host, CompanionKind.TAXI_POT, TransportType.TAXI, 4);
        ReflectionTestUtils.setField(taxiPot, "status", status);
        return taxiPot;
    }

    public static Companion companionPost(User host) {
        return recruiting(host, CompanionKind.COMPANION, TransportType.SUBWAY, 10);
    }

    private static Companion recruiting(
            User host, CompanionKind kind, TransportType transportType, int capacity) {
        return Companion.createCompanionPost(host, kind, transportType, null,
                "판교역", new BigDecimal("37.394500"), new BigDecimal("127.111200"),
                "강남역", new BigDecimal("37.497900"), new BigDecimal("127.027600"),
                DEPARTURE_AT, capacity);
    }
}
