package com.ktb.moyeota.domain.companion.service;

import static com.ktb.moyeota.domain.companion.entity.CompanionStatus.RECRUITING;
import static com.ktb.moyeota.fixture.CompanionFixture.DEPARTURE_AT;
import static com.ktb.moyeota.fixture.CompanionFixture.DEST_LAT;
import static com.ktb.moyeota.fixture.CompanionFixture.DEST_LNG;
import static com.ktb.moyeota.fixture.CompanionFixture.ORIGIN_LAT;
import static com.ktb.moyeota.fixture.CompanionFixture.ORIGIN_LNG;
import static com.ktb.moyeota.fixture.CompanionFixture.carpool;
import static com.ktb.moyeota.fixture.CompanionFixture.taxiPot;
import static com.ktb.moyeota.fixture.UserFixture.user;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;

import com.ktb.moyeota.domain.companion.entity.Companion;
import com.ktb.moyeota.domain.companion.entity.StraightLineRideDuration;
import com.ktb.moyeota.domain.user.entity.User;
import com.ktb.moyeota.global.external.kakao.KakaoNaviClient;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@DataJpaTest
@Import(CompanionEtaService.class)
class CompanionEtaServiceTest {

    private static final LocalDateTime STARTED_AT = DEPARTURE_AT.plusMinutes(3);

    @Autowired
    private CompanionEtaService companionEtaService;

    @Autowired
    private TestEntityManager entityManager;

    @MockitoBean
    private KakaoNaviClient kakaoNaviClient;

    @Test
    @DisplayName("운행 중인 택시팟은 길찾기 소요 시간을 운행 시작 시각에 더해 도착 예정 시각을 바꾼다")
    void estimatesTaxiPot() {
        Companion pot = startedRide(taxiPot(host(), RECRUITING, 2));
        given(kakaoNaviClient.estimateDuration(ORIGIN_LAT, ORIGIN_LNG, DEST_LAT, DEST_LNG))
                .willReturn(Optional.of(Duration.ofMinutes(20)));

        boolean estimated = companionEtaService.estimate(pot.getId(), STARTED_AT);

        assertThat(estimated).isTrue();
        assertThat(reload(pot).getEtaAt()).isEqualTo(STARTED_AT.plusMinutes(20));
    }

    @Test
    @DisplayName("운행 중인 카풀도 같은 방식으로 도착 예정 시각을 바꾼다")
    void estimatesCarpool() {
        Companion carpool = startedRide(carpool(host(), RECRUITING, 2));
        given(kakaoNaviClient.estimateDuration(ORIGIN_LAT, ORIGIN_LNG, DEST_LAT, DEST_LNG))
                .willReturn(Optional.of(Duration.ofMinutes(35)));

        companionEtaService.estimate(carpool.getId(), STARTED_AT);

        assertThat(reload(carpool).getEtaAt()).isEqualTo(STARTED_AT.plusMinutes(35));
    }

    @Test
    @DisplayName("길찾기가 실패하면 다시 시도하도록 예외를 던지고 기본 도착 예정 시각은 그대로 둔다")
    void throwsWhenNaviUnavailable() {
        Companion pot = startedRide(taxiPot(host(), RECRUITING, 2));
        given(kakaoNaviClient.estimateDuration(ORIGIN_LAT, ORIGIN_LNG, DEST_LAT, DEST_LNG))
                .willReturn(Optional.empty());

        assertThatThrownBy(() -> companionEtaService.estimate(pot.getId(), STARTED_AT))
                .isInstanceOf(EtaUnavailableException.class);
        assertThat(reload(pot).getEtaAt()).isEqualTo(STARTED_AT.plus(
                StraightLineRideDuration.estimate(ORIGIN_LAT, ORIGIN_LNG, DEST_LAT, DEST_LNG)));
    }

    @Test
    @DisplayName("이미 운행이 끝난 팟은 길찾기를 부르지 않고 넘긴다")
    void skipsFinishedRide() {
        Companion pot = startedRide(taxiPot(host(), RECRUITING, 2));
        pot.endRide();
        entityManager.flush();

        boolean estimated = companionEtaService.estimate(pot.getId(), STARTED_AT);

        assertThat(estimated).isFalse();
    }

    private User host() {
        return entityManager.persist(user("방장"));
    }

    private Companion startedRide(Companion companion) {
        companion.startRide(STARTED_AT);
        return entityManager.persistAndFlush(companion);
    }

    private Companion reload(Companion companion) {
        entityManager.flush();
        entityManager.clear();
        return entityManager.find(Companion.class, companion.getId());
    }
}
