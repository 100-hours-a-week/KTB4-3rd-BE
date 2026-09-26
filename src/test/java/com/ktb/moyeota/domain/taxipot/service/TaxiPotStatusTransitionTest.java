package com.ktb.moyeota.domain.taxipot.service;

import static com.ktb.moyeota.fixture.CompanionFixture.DEPARTURE_AT;
import static com.ktb.moyeota.fixture.CompanionFixture.taxiPot;
import static com.ktb.moyeota.fixture.ParticipantFixture.participant;
import static com.ktb.moyeota.fixture.UserFixture.user;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ktb.moyeota.domain.companion.entity.Companion;
import com.ktb.moyeota.domain.companion.entity.CompanionStatus;
import com.ktb.moyeota.domain.chat.entity.OutcomeStatus;
import com.ktb.moyeota.domain.user.entity.User;
import com.ktb.moyeota.global.exception.BusinessException;
import java.time.Clock;
import java.time.ZoneId;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;

@DataJpaTest
@Import({TaxiPotService.class, TaxiPotStatusTransitionTest.FixedClock.class})
class TaxiPotStatusTransitionTest {

    @Autowired
    private TaxiPotService taxiPotService;

    @Autowired
    private TestEntityManager entityManager;

    @ParameterizedTest(name = "{0} → {1}", quoteTextArguments = false)
    @CsvSource({
            "RECRUITING,  IN_PROGRESS",
            "IN_PROGRESS, COMPLETED"
    })
    void allowed(CompanionStatus current, CompanionStatus target) {
        User host = entityManager.persist(user("방장"));
        Companion pot = persistTaxiPot(host, current);

        taxiPotService.changeStatus(host.getId(), pot.getId(), target);

        assertThat(reload(pot).getStatus()).isEqualTo(target);
    }

    @ParameterizedTest(name = "{0} → {1} 은 {2}", quoteTextArguments = false)
    @CsvSource({
            "RECRUITING,  COMPLETED,   INVALID_STATE_TRANSITION",
            "IN_PROGRESS, IN_PROGRESS, INVALID_STATE_TRANSITION",
            "COMPLETED,   IN_PROGRESS, INVALID_STATE_TRANSITION",
            "COMPLETED,   COMPLETED,   INVALID_STATE_TRANSITION",
            "CANCELED,    IN_PROGRESS, TAXI_POT_NOT_FOUND",
            "CANCELED,    COMPLETED,   TAXI_POT_NOT_FOUND"
    })
    void rejected(CompanionStatus current, CompanionStatus target, String expectedCode) {
        User host = entityManager.persist(user("방장"));
        Companion pot = persistTaxiPot(host, current);

        assertThatThrownBy(() -> taxiPotService.changeStatus(host.getId(), pot.getId(), target))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode().name())
                .isEqualTo(expectedCode);
        assertThat(reload(pot).getStatus()).isEqualTo(current);
    }

    private Companion persistTaxiPot(User host, CompanionStatus status) {
        Companion pot = entityManager.persist(taxiPot(host, status, 2));
        OutcomeStatus outcome =
                status == CompanionStatus.CANCELED ? OutcomeStatus.INCOMPLETE : OutcomeStatus.PENDING;
        entityManager.persistAndFlush(participant(pot, host, outcome));
        return pot;
    }

    private Companion reload(Companion pot) {
        entityManager.flush();
        entityManager.clear();
        return entityManager.find(Companion.class, pot.getId());
    }

    @TestConfiguration
    static class FixedClock {

        @Bean
        Clock clock() {
            ZoneId kst = ZoneId.of("Asia/Seoul");
            return Clock.fixed(DEPARTURE_AT.atZone(kst).toInstant(), kst);
        }
    }
}
