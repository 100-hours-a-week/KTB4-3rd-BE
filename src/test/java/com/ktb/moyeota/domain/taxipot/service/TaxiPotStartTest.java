package com.ktb.moyeota.domain.taxipot.service;

import static com.ktb.moyeota.fixture.CompanionFixture.DEPARTURE_AT;
import static com.ktb.moyeota.fixture.ParticipantFixture.participant;
import static com.ktb.moyeota.fixture.TaxiPotFixture.startCommand;
import static com.ktb.moyeota.fixture.UserFixture.bankAccountHolder;
import static org.assertj.core.api.Assertions.assertThat;

import com.ktb.moyeota.domain.chat.entity.OutcomeStatus;
import com.ktb.moyeota.domain.companion.entity.Companion;
import com.ktb.moyeota.domain.companion.entity.CompanionStatus;
import com.ktb.moyeota.domain.taxipot.model.CurrentTaxiPot;
import com.ktb.moyeota.domain.taxipot.repository.TaxiPotParticipantRepository;
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
@Import({TaxiPotService.class, TaxiPotStartTest.FixedClock.class})
class TaxiPotStartTest {

    private static final LocalDateTime NOW = DEPARTURE_AT.minusHours(1);

    @Autowired
    private TaxiPotService taxiPotService;

    @Autowired
    private TaxiPotParticipantRepository taxiPotParticipantRepository;

    @Autowired
    private TestEntityManager entityManager;

    @Test
    @DisplayName("같은 조건의 팟이 없으면 새로 열고 방장이 된다")
    void opensWhenNoMatch() {
        User me = entityManager.persist(bankAccountHolder("첫사람"));

        CurrentTaxiPot pot = taxiPotService.start(me.getId(), startCommand(DEPARTURE_AT));

        assertThat(pot.status()).isEqualTo(CompanionStatus.RECRUITING);
        assertThat(pot.currentCount()).isEqualTo(1);
        assertThat(pot.capacity()).isEqualTo(4);
        assertThat(reload(pot).getHost().getId()).isEqualTo(me.getId());
    }

    @Test
    @DisplayName("같은 조건의 팟이 있으면 합류하고 응답에 늘어난 인원이 담긴다")
    void joinsSameConditions() {
        User host = entityManager.persist(bankAccountHolder("방장"));
        User joiner = entityManager.persist(bankAccountHolder("합류자"));
        CurrentTaxiPot opened = taxiPotService.start(host.getId(), startCommand(DEPARTURE_AT));

        CurrentTaxiPot joined = taxiPotService.start(joiner.getId(), startCommand(DEPARTURE_AT));

        assertThat(joined.id()).isEqualTo(opened.id());
        assertThat(joined.currentCount()).isEqualTo(2);
        assertThat(reload(joined).getCurrentCount()).isEqualTo(2);
        assertThat(taxiPotParticipantRepository.findCurrentTaxiPot(joiner.getId())).isPresent();
    }

    @Test
    @DisplayName("초 단위가 달라도 같은 분이면 같은 팟에 합류한다")
    void matchesByMinute() {
        User host = entityManager.persist(bankAccountHolder("방장"));
        User joiner = entityManager.persist(bankAccountHolder("합류자"));

        CurrentTaxiPot opened = taxiPotService.start(host.getId(), startCommand(DEPARTURE_AT.plusSeconds(10)));
        CurrentTaxiPot joined = taxiPotService.start(joiner.getId(), startCommand(DEPARTURE_AT.plusSeconds(50)));

        assertThat(joined.id()).isEqualTo(opened.id());
        assertThat(reload(opened).getDepartureAt()).isEqualTo(DEPARTURE_AT);
    }

    @Test
    @DisplayName("나갔던 팟에 같은 조건으로 다시 매칭되면 예전 참여를 되살려 합류한다")
    void rejoinsPotLeftBefore() {
        User host = entityManager.persist(bankAccountHolder("방장"));
        User returning = entityManager.persist(bankAccountHolder("돌아온사람"));
        CurrentTaxiPot opened = taxiPotService.start(host.getId(), startCommand(DEPARTURE_AT));
        entityManager.persistAndFlush(participant(reload(opened), returning, OutcomeStatus.INCOMPLETE));

        CurrentTaxiPot joined = taxiPotService.start(returning.getId(), startCommand(DEPARTURE_AT));

        assertThat(joined.id()).isEqualTo(opened.id());
        assertThat(joined.currentCount()).isEqualTo(2);
        assertThat(taxiPotParticipantRepository.findCurrentTaxiPot(returning.getId())).isPresent();
    }

    @Test
    @DisplayName("출발 시각이 다르면 다른 팟을 연다")
    void differentDepartureOpensNew() {
        User first = entityManager.persist(bankAccountHolder("첫사람"));
        User second = entityManager.persist(bankAccountHolder("둘째"));

        CurrentTaxiPot a = taxiPotService.start(first.getId(), startCommand(DEPARTURE_AT));
        CurrentTaxiPot b = taxiPotService.start(second.getId(), startCommand(DEPARTURE_AT.plusMinutes(30)));

        assertThat(b.id()).isNotEqualTo(a.id());
    }

    @Test
    @DisplayName("4명이 차면 다섯 번째 사람은 새 팟을 연다")
    void fifthOpensNew() {
        CurrentTaxiPot full = null;
        for (int i = 1; i <= 4; i++) {
            User user = entityManager.persist(bankAccountHolder("탑승자" + i));
            full = taxiPotService.start(user.getId(), startCommand(DEPARTURE_AT));
        }
        User fifth = entityManager.persist(bankAccountHolder("다섯째"));

        CurrentTaxiPot next = taxiPotService.start(fifth.getId(), startCommand(DEPARTURE_AT));

        assertThat(full.currentCount()).isEqualTo(4);
        assertThat(next.id()).isNotEqualTo(full.id());
        assertThat(next.currentCount()).isEqualTo(1);
    }

    private Companion reload(CurrentTaxiPot pot) {
        entityManager.flush();
        entityManager.clear();
        return entityManager.find(Companion.class, pot.id());
    }

    @TestConfiguration
    static class FixedClock {

        @Bean
        Clock clock() {
            ZoneId kst = ZoneId.of("Asia/Seoul");
            return Clock.fixed(NOW.atZone(kst).toInstant(), kst);
        }
    }
}
