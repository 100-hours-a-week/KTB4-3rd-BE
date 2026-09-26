package com.ktb.moyeota.domain.taxipot.service;

import static com.ktb.moyeota.domain.companion.entity.CompanionStatus.COMPLETED;
import static com.ktb.moyeota.domain.companion.entity.CompanionStatus.IN_PROGRESS;
import static com.ktb.moyeota.domain.companion.entity.CompanionStatus.RECRUITING;
import static com.ktb.moyeota.domain.chat.entity.OutcomeStatus.PENDING;
import static com.ktb.moyeota.fixture.CompanionFixture.DEPARTURE_AT;
import static com.ktb.moyeota.fixture.CompanionFixture.taxiPot;
import static com.ktb.moyeota.fixture.ParticipantFixture.participant;
import static com.ktb.moyeota.fixture.UserFixture.user;
import static org.assertj.core.api.Assertions.assertThat;

import com.ktb.moyeota.domain.chat.entity.ChatRoom;
import com.ktb.moyeota.domain.chat.service.ChatSystemMessageService;
import com.ktb.moyeota.domain.companion.entity.Companion;
import com.ktb.moyeota.domain.companion.entity.CompanionStatus;
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
@Import({TaxiPotService.class, ChatSystemMessageService.class, TaxiPotStatusChangeTest.FixedClock.class})
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
        entityManager.persist(ChatRoom.create(pot));
        entityManager.persistAndFlush(participant(pot, host, PENDING));

        assertThat(taxiPotService.changeStatus(host.getId(), pot.getId(), IN_PROGRESS).status())
                .isEqualTo(IN_PROGRESS);
    }

    @Test
    @DisplayName("운행을 시작하면 수정 시각이 갱신된다")
    void touchesUpdatedAt() {
        User host = entityManager.persist(user("방장"));
        Companion pot = entityManager.persist(taxiPot(host, RECRUITING, 2));
        entityManager.persist(ChatRoom.create(pot));
        entityManager.persistAndFlush(participant(pot, host, PENDING));
        LocalDateTime createdUpdatedAt = pot.getUpdatedAt();

        taxiPotService.changeStatus(host.getId(), pot.getId(), IN_PROGRESS);
        entityManager.flush();
        entityManager.clear();

        assertThat(entityManager.find(Companion.class, pot.getId()).getUpdatedAt()).isAfter(createdUpdatedAt);
    }

    @Test
    @DisplayName("운행을 시작하면 도착 예정 시각이 시작 시각의 1시간 뒤로 저장된다")
    void savesDefaultEta() {
        Companion pot = persistTaxiPot(RECRUITING);

        taxiPotService.changeStatus(pot.getHost().getId(), pot.getId(), IN_PROGRESS);

        assertThat(reload(pot).getEtaAt()).isEqualTo(DEPARTURE_AT.plusHours(1));
    }

    @Test
    @DisplayName("운행 중인 팟은 받은 도착 예정 시각으로 바뀐다")
    void estimatesArrivalWhileRiding() {
        Companion pot = persistTaxiPot(IN_PROGRESS);

        taxiPotService.estimateArrival(pot.getId(), DEPARTURE_AT.plusMinutes(20));

        assertThat(reload(pot).getEtaAt()).isEqualTo(DEPARTURE_AT.plusMinutes(20));
    }

    @Test
    @DisplayName("운행이 끝난 팟은 도착 예정 시각이 바뀌지 않는다")
    void keepsEtaAfterRide() {
        Companion pot = persistTaxiPot(COMPLETED);

        taxiPotService.estimateArrival(pot.getId(), DEPARTURE_AT.plusMinutes(20));

        assertThat(reload(pot).getEtaAt()).isNull();
    }

    private Companion persistTaxiPot(CompanionStatus status) {
        User host = entityManager.persist(user("방장"));
        Companion pot = entityManager.persist(taxiPot(host, status, 2));
        entityManager.persist(ChatRoom.create(pot));
        entityManager.persistAndFlush(participant(pot, host, PENDING));
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
