package com.ktb.moyeota.domain.taxipot.service;

import static com.ktb.moyeota.fixture.CompanionFixture.DEPARTURE_AT;
import static com.ktb.moyeota.fixture.TaxiPotFixture.startCommand;
import static com.ktb.moyeota.fixture.UserFixture.bankAccountHolder;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ktb.moyeota.domain.companion.entity.Companion;
import com.ktb.moyeota.domain.companion.entity.CompanionStatus;
import com.ktb.moyeota.domain.taxipot.repository.TaxiPotParticipantRepository;
import com.ktb.moyeota.domain.taxipot.error.TaxiPotErrorCode;
import com.ktb.moyeota.domain.taxipot.model.CurrentTaxiPot;
import com.ktb.moyeota.domain.user.entity.User;
import com.ktb.moyeota.global.exception.BusinessException;
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
@Import({TaxiPotService.class, TaxiPotLeaveTest.FixedClock.class})
class TaxiPotLeaveTest {

    private static final LocalDateTime NOW = DEPARTURE_AT.minusHours(1);

    @Autowired
    private TaxiPotService taxiPotService;

    @Autowired
    private TaxiPotParticipantRepository taxiPotParticipantRepository;

    @Autowired
    private TestEntityManager entityManager;

    @Test
    @DisplayName("동승자가 나가면 인원이 줄고 나간 동승자는 참여 중인 택시팟 매칭이 없다")
    void memberLeaves() {
        User host = entityManager.persist(bankAccountHolder("방장"));
        User member = entityManager.persist(bankAccountHolder("동승자"));
        CurrentTaxiPot pot = taxiPotService.start(host.getId(), startCommand(DEPARTURE_AT));
        taxiPotService.start(member.getId(), startCommand(DEPARTURE_AT));

        taxiPotService.leave(member.getId(), pot.id());

        assertThat(reload(pot).getCurrentCount()).isEqualTo(1);
        assertThat(taxiPotParticipantRepository.findCurrentTaxiPot(member.getId())).isEmpty();
    }

    @Test
    @DisplayName("방장이 나가면 가장 먼저 들어왔던 사람이 방장이 된다")
    void hostLeaves() {
        User host = entityManager.persist(bankAccountHolder("방장"));
        User first = entityManager.persist(bankAccountHolder("먼저온사람"));
        User second = entityManager.persist(bankAccountHolder("나중온사람"));
        CurrentTaxiPot pot = taxiPotService.start(host.getId(), startCommand(DEPARTURE_AT));
        taxiPotService.start(first.getId(), startCommand(DEPARTURE_AT));
        taxiPotService.start(second.getId(), startCommand(DEPARTURE_AT));

        taxiPotService.leave(host.getId(), pot.id());

        assertThat(reload(pot).getHost().getId()).isEqualTo(first.getId());
    }

    @Test
    @DisplayName("마지막 한 명이 나가면 취소된다")
    void lastOneLeaves() {
        User host = entityManager.persist(bankAccountHolder("혼자"));
        CurrentTaxiPot pot = taxiPotService.start(host.getId(), startCommand(DEPARTURE_AT));

        taxiPotService.leave(host.getId(), pot.id());

        assertThat(reload(pot).getStatus()).isEqualTo(CompanionStatus.CANCELED);
    }

    @Test
    @DisplayName("정원이 찬 팟에서 한 명이 나가면 다시 다른 사람이 합류할 수 있다")
    void seatOpensAgain() {
        CurrentTaxiPot full = null;
        User leaving = null;
        for (int i = 1; i <= 4; i++) {
            leaving = entityManager.persist(bankAccountHolder("탑승자" + i));
            full = taxiPotService.start(leaving.getId(), startCommand(DEPARTURE_AT));
        }
        assertThat(full.currentCount()).isEqualTo(full.capacity());
        taxiPotService.leave(leaving.getId(), full.id());
        User newcomer = entityManager.persist(bankAccountHolder("새로온사람"));

        CurrentTaxiPot joined = taxiPotService.start(newcomer.getId(), startCommand(DEPARTURE_AT));

        assertThat(joined.id()).isEqualTo(full.id());
        assertThat(joined.currentCount()).isEqualTo(4);
    }

    @Test
    @DisplayName("나갔다가 같은 조건으로 다시 매칭하면 같은 팟에 예전 참여를 되살려 합류한다")
    void leaveThenRejoin() {
        User host = entityManager.persist(bankAccountHolder("방장"));
        User member = entityManager.persist(bankAccountHolder("동승자"));
        CurrentTaxiPot pot = taxiPotService.start(host.getId(), startCommand(DEPARTURE_AT));
        taxiPotService.start(member.getId(), startCommand(DEPARTURE_AT));
        taxiPotService.leave(member.getId(), pot.id());

        CurrentTaxiPot rejoined = taxiPotService.start(member.getId(), startCommand(DEPARTURE_AT));

        assertThat(rejoined.id()).isEqualTo(pot.id());
        assertThat(rejoined.currentCount()).isEqualTo(2);
        assertThat(taxiPotParticipantRepository.findCurrentTaxiPot(member.getId())).isPresent();
    }

    @Test
    @DisplayName("이미 나간 사람이 다시 나가면 TAXI_POT_NOT_FOUND다")
    void leaveTwice() {
        User host = entityManager.persist(bankAccountHolder("방장"));
        User member = entityManager.persist(bankAccountHolder("동승자"));
        CurrentTaxiPot pot = taxiPotService.start(host.getId(), startCommand(DEPARTURE_AT));
        taxiPotService.start(member.getId(), startCommand(DEPARTURE_AT));
        taxiPotService.leave(member.getId(), pot.id());

        assertThatThrownBy(() -> taxiPotService.leave(member.getId(), pot.id()))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(TaxiPotErrorCode.TAXI_POT_NOT_FOUND);
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
