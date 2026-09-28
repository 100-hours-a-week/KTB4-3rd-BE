package com.ktb.moyeota.domain.taxipot.service;

import com.ktb.moyeota.domain.taxipot.event.TaxiPotRideStartedEvent;
import com.ktb.moyeota.global.external.kakao.KakaoNaviClient;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
@RequiredArgsConstructor
public class EtaRefresher {

    private final KakaoNaviClient kakaoNaviClient;
    private final TaxiPotService taxiPotService;

    @Async
    @TransactionalEventListener
    public void refresh(TaxiPotRideStartedEvent event) {
        kakaoNaviClient.estimateDuration(event.originLat(), event.originLng(), event.destLat(), event.destLng())
                .ifPresent(duration -> taxiPotService.estimateArrival(
                        event.taxiPotId(), event.startedAt().plus(duration)));
    }
}
