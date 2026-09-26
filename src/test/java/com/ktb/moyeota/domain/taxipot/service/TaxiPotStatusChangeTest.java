package com.ktb.moyeota.domain.taxipot.service;

import static com.ktb.moyeota.domain.companion.entity.CompanionStatus.IN_PROGRESS;
import static com.ktb.moyeota.domain.companion.entity.CompanionStatus.RECRUITING;
import static com.ktb.moyeota.domain.chat.entity.OutcomeStatus.PENDING;
import static com.ktb.moyeota.fixture.CompanionFixture.DEPARTURE_AT;
import static com.ktb.moyeota.fixture.CompanionFixture.taxiPot;
import static com.ktb.moyeota.fixture.ParticipantFixture.participant;
import static com.ktb.moyeota.fixture.UserFixture.user;
import static org.assertj.core.api.Assertions.assertThat;

import com.ktb.moyeota.domain.companion.entity.Companion;
import com.ktb.moyeota.domain.user.entity.User;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;

@DataJpaTest
@Import({TaxiPotService.class, TaxiPotStatusChangeTest.FixedClock.class})
class TaxiPotStatusChangeTest {

    @Autowired
    private TaxiPotService taxiPotService;

    @Autowired
    private TestEntityManager entityManager;

    @Test
    @DisplayName("운행을 시작하면 응답에 바뀐 상태가 담긴다")
    void responseShowsNewStatus() {
        User host = entityManager.persist(user("방장"));
        Companion pot = entityManager.persist(taxiPot(host, RECRUITING, 2));
        entityManager.persistAndFlush(participant(pot, host, PENDING));

        assertThat(taxiPotService.changeStatus(host.getId(), pot.getId(), IN_PROGRESS).status())
                .isEqualTo(IN_PROGRESS);
    }

    @Test
    @DisplayName("운행을 시작하면 수정 시각이 갱신된다")
    void touchesUpdatedAt() {
        User host = entityManager.persist(user("방장"));
        Companion pot = entityManager.persist(taxiPot(host, RECRUITING, 2));
        entityManager.persistAndFlush(participant(pot, host, PENDING));
        LocalDateTime createdUpdatedAt = pot.getUpdatedAt();

        taxiPotService.changeStatus(host.getId(), pot.getId(), IN_PROGRESS);
        entityManager.flush();
        entityManager.clear();

        assertThat(entityManager.find(Companion.class, pot.getId()).getUpdatedAt()).isAfter(createdUpdatedAt);
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
