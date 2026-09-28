package com.ktb.moyeota.fixture;

import static com.ktb.moyeota.fixture.CompanionFixture.DEST_LAT;
import static com.ktb.moyeota.fixture.CompanionFixture.DEST_LNG;
import static com.ktb.moyeota.fixture.CompanionFixture.DEST_NAME;
import static com.ktb.moyeota.fixture.CompanionFixture.ORIGIN_LAT;
import static com.ktb.moyeota.fixture.CompanionFixture.ORIGIN_LNG;
import static com.ktb.moyeota.fixture.CompanionFixture.ORIGIN_NAME;

import com.ktb.moyeota.domain.taxipot.model.TaxiPotStartCommand;
import java.time.LocalDateTime;

public final class TaxiPotFixture {

    private TaxiPotFixture() {
    }

    public static TaxiPotStartCommand startCommand(LocalDateTime departureAt) {
        return new TaxiPotStartCommand(
                ORIGIN_NAME, ORIGIN_LAT, ORIGIN_LNG, DEST_NAME, DEST_LAT, DEST_LNG, departureAt);
    }
}
