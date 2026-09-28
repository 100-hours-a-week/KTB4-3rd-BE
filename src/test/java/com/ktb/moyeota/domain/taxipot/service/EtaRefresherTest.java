package com.ktb.moyeota.domain.taxipot.service;

import static com.ktb.moyeota.fixture.CompanionFixture.DEPARTURE_AT;
import static com.ktb.moyeota.fixture.CompanionFixture.DEST_LAT;
import static com.ktb.moyeota.fixture.CompanionFixture.DEST_LNG;
import static com.ktb.moyeota.fixture.CompanionFixture.ORIGIN_LAT;
import static com.ktb.moyeota.fixture.CompanionFixture.ORIGIN_LNG;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.ktb.moyeota.domain.taxipot.event.TaxiPotRideStartedEvent;
import com.ktb.moyeota.global.external.kakao.KakaoNaviClient;
import java.time.Duration;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class EtaRefresherTest {

    private static final TaxiPotRideStartedEvent RIDE_STARTED = new TaxiPotRideStartedEvent(
            30L, DEPARTURE_AT, ORIGIN_LAT, ORIGIN_LNG, DEST_LAT, DEST_LNG);

    @Mock
    private KakaoNaviClient kakaoNaviClient;

    @Mock
    private TaxiPotService taxiPotService;

    @InjectMocks
    private EtaRefresher etaRefresher;

    @Test
    @DisplayName("소요 시간을 받으면 운행 시작 시각에 더해 도착 예정 시각을 바꾼다")
    void estimatesFromStartedAt() {
        given(kakaoNaviClient.estimateDuration(ORIGIN_LAT, ORIGIN_LNG, DEST_LAT, DEST_LNG))
                .willReturn(Optional.of(Duration.ofMinutes(20)));

        etaRefresher.refresh(RIDE_STARTED);

        verify(taxiPotService).estimateArrival(30L, DEPARTURE_AT.plusMinutes(20));
    }

    @Test
    @DisplayName("소요 시간을 못 받으면 기본 도착 예정 시각을 그대로 둔다")
    void keepsDefaultWhenUnavailable() {
        given(kakaoNaviClient.estimateDuration(ORIGIN_LAT, ORIGIN_LNG, DEST_LAT, DEST_LNG))
                .willReturn(Optional.empty());

        etaRefresher.refresh(RIDE_STARTED);

        verify(taxiPotService, never()).estimateArrival(any(), any());
    }
}
