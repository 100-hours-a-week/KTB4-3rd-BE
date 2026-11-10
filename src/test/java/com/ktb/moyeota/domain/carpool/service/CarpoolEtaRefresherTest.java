package com.ktb.moyeota.domain.carpool.service;

import static com.ktb.moyeota.domain.companion.entity.CompanionStatus.COMPLETED;
import static com.ktb.moyeota.domain.companion.entity.CompanionStatus.IN_PROGRESS;
import static com.ktb.moyeota.domain.companion.entity.CompanionStatus.RECRUITING;
import static com.ktb.moyeota.fixture.CompanionFixture.DEPARTURE_AT;
import static com.ktb.moyeota.fixture.CompanionFixture.DEST_LAT;
import static com.ktb.moyeota.fixture.CompanionFixture.DEST_LNG;
import static com.ktb.moyeota.fixture.CompanionFixture.ORIGIN_LAT;
import static com.ktb.moyeota.fixture.CompanionFixture.ORIGIN_LNG;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

import com.ktb.moyeota.domain.carpool.event.CarpoolRideStartedEvent;
import com.ktb.moyeota.domain.companion.entity.Companion;
import com.ktb.moyeota.global.external.kakao.KakaoNaviClient;
import java.time.Duration;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class CarpoolEtaRefresherTest extends CarpoolRideTestSupport {

    private final KakaoNaviClient kakaoNaviClient = mock(KakaoNaviClient.class);

    private CarpoolEtaRefresher carpoolEtaRefresher;

    @BeforeEach
    void setUp() {
        carpoolEtaRefresher = new CarpoolEtaRefresher(kakaoNaviClient, carpoolRideService);
    }

    @Test
    @DisplayName("소요 시간을 받으면 도착 예정 시각이 운행 시작 시각 + 소요 시간으로 바뀐다")
    void estimatesFromStartedAt() {
        Companion carpool = startedCarpool();
        given(kakaoNaviClient.estimateDuration(ORIGIN_LAT, ORIGIN_LNG, DEST_LAT, DEST_LNG))
                .willReturn(Optional.of(Duration.ofMinutes(20)));

        carpoolEtaRefresher.refresh(rideStarted(carpool));

        assertThat(reload(carpool).getEtaAt()).isEqualTo(DEPARTURE_AT.plusMinutes(20));
    }

    @Test
    @DisplayName("소요 시간을 못 받으면 기본 도착 예정 시각(시작 1시간 뒤)이 그대로 남는다")
    void keepsDefaultWhenUnavailable() {
        Companion carpool = startedCarpool();
        given(kakaoNaviClient.estimateDuration(ORIGIN_LAT, ORIGIN_LNG, DEST_LAT, DEST_LNG))
                .willReturn(Optional.empty());

        carpoolEtaRefresher.refresh(rideStarted(carpool));

        assertThat(reload(carpool).getEtaAt()).isEqualTo(DEPARTURE_AT.plusHours(1));
    }

    @Test
    @DisplayName("소요 시간이 늦게 와서 그사이 운행이 끝났으면 도착 예정 시각을 바꾸지 않는다")
    void keepsEtaAfterRideEnded() {
        Companion carpool = startedCarpool();
        carpoolRideService.changeStatus(carpool.getHost().getId(), carpool.getId(), COMPLETED);
        given(kakaoNaviClient.estimateDuration(ORIGIN_LAT, ORIGIN_LNG, DEST_LAT, DEST_LNG))
                .willReturn(Optional.of(Duration.ofMinutes(20)));

        carpoolEtaRefresher.refresh(rideStarted(carpool));

        assertThat(reload(carpool).getEtaAt()).isEqualTo(DEPARTURE_AT.plusHours(1));
    }

    private Companion startedCarpool() {
        Companion carpool = persistCarpool(RECRUITING, 2);
        carpoolRideService.changeStatus(carpool.getHost().getId(), carpool.getId(), IN_PROGRESS);
        return carpool;
    }

    private static CarpoolRideStartedEvent rideStarted(Companion carpool) {
        return new CarpoolRideStartedEvent(carpool.getId(), DEPARTURE_AT, ORIGIN_LAT, ORIGIN_LNG, DEST_LAT, DEST_LNG);
    }
}
