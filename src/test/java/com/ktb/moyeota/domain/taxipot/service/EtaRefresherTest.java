package com.ktb.moyeota.domain.taxipot.service;

import static com.ktb.moyeota.domain.chat.entity.OutcomeStatus.PENDING;
import static com.ktb.moyeota.domain.companion.entity.CompanionStatus.COMPLETED;
import static com.ktb.moyeota.domain.companion.entity.CompanionStatus.IN_PROGRESS;
import static com.ktb.moyeota.domain.companion.entity.CompanionStatus.RECRUITING;
import static com.ktb.moyeota.fixture.CompanionFixture.DEPARTURE_AT;
import static com.ktb.moyeota.fixture.CompanionFixture.DEST_LAT;
import static com.ktb.moyeota.fixture.CompanionFixture.DEST_LNG;
import static com.ktb.moyeota.fixture.CompanionFixture.ORIGIN_LAT;
import static com.ktb.moyeota.fixture.CompanionFixture.ORIGIN_LNG;
import static com.ktb.moyeota.fixture.CompanionFixture.taxiPot;
import static com.ktb.moyeota.fixture.ParticipantFixture.participant;
import static com.ktb.moyeota.fixture.UserFixture.user;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

import com.ktb.moyeota.domain.chat.entity.ChatRoom;
import com.ktb.moyeota.domain.chat.service.ChatSystemMessageService;
import com.ktb.moyeota.domain.companion.entity.Companion;
import com.ktb.moyeota.domain.taxipot.event.TaxiPotRideStartedEvent;
import com.ktb.moyeota.domain.user.entity.User;
import com.ktb.moyeota.global.external.kakao.KakaoNaviClient;
import java.time.Clock;
import java.time.Duration;
import java.time.ZoneId;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;

@DataJpaTest
@Import({TaxiPotService.class, ChatSystemMessageService.class, EtaRefresherTest.FixedClock.class})
class EtaRefresherTest {

    private final KakaoNaviClient kakaoNaviClient = mock(KakaoNaviClient.class);

    @Autowired
    private TaxiPotService taxiPotService;

    @Autowired
    private TestEntityManager entityManager;

    private EtaRefresher etaRefresher;

    @BeforeEach
    void setUp() {
        etaRefresher = new EtaRefresher(kakaoNaviClient, taxiPotService);
    }

    @Test
    @DisplayName("소요 시간을 받으면 운행 시작 시각에 더해 도착 예정 시각을 바꾼다")
    void estimatesFromStartedAt() {
        Companion pot = startedTaxiPot();
        given(kakaoNaviClient.estimateDuration(ORIGIN_LAT, ORIGIN_LNG, DEST_LAT, DEST_LNG))
                .willReturn(Optional.of(Duration.ofMinutes(20)));

        etaRefresher.refresh(rideStarted(pot));

        assertThat(reload(pot).getEtaAt()).isEqualTo(DEPARTURE_AT.plusMinutes(20));
    }

    @Test
    @DisplayName("소요 시간을 못 받으면 기본 도착 예정 시각을 그대로 둔다")
    void keepsDefaultWhenUnavailable() {
        Companion pot = startedTaxiPot();
        given(kakaoNaviClient.estimateDuration(ORIGIN_LAT, ORIGIN_LNG, DEST_LAT, DEST_LNG))
                .willReturn(Optional.empty());

        etaRefresher.refresh(rideStarted(pot));

        assertThat(reload(pot).getEtaAt()).isEqualTo(DEPARTURE_AT.plusHours(1));
    }

    @Test
    @DisplayName("소요 시간이 늦게 와서 그사이 운행이 끝났으면 도착 예정 시각을 바꾸지 않는다")
    void keepsEtaAfterRideEnded() {
        Companion pot = startedTaxiPot();
        taxiPotService.changeStatus(pot.getHost().getId(), pot.getId(), COMPLETED);
        given(kakaoNaviClient.estimateDuration(ORIGIN_LAT, ORIGIN_LNG, DEST_LAT, DEST_LNG))
                .willReturn(Optional.of(Duration.ofMinutes(20)));

        etaRefresher.refresh(rideStarted(pot));

        assertThat(reload(pot).getEtaAt()).isEqualTo(DEPARTURE_AT.plusHours(1));
    }

    private Companion startedTaxiPot() {
        User host = entityManager.persist(user("방장"));
        Companion pot = entityManager.persist(taxiPot(host, RECRUITING, 2));
        entityManager.persist(ChatRoom.create(pot));
        entityManager.persistAndFlush(participant(pot, host, PENDING));
        taxiPotService.changeStatus(host.getId(), pot.getId(), IN_PROGRESS);
        return pot;
    }

    private static TaxiPotRideStartedEvent rideStarted(Companion pot) {
        return new TaxiPotRideStartedEvent(pot.getId(), DEPARTURE_AT, ORIGIN_LAT, ORIGIN_LNG, DEST_LAT, DEST_LNG);
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
