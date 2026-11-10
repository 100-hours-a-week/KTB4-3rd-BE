package com.ktb.moyeota.domain.carpool.service;

import com.ktb.moyeota.domain.carpool.event.CarpoolRideStartedEvent;
import com.ktb.moyeota.global.external.kakao.KakaoNaviClient;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
@RequiredArgsConstructor
public class CarpoolEtaRefresher {

    private final KakaoNaviClient kakaoNaviClient;
    private final CarpoolRideService carpoolRideService;

    @Async
    @TransactionalEventListener
    public void refresh(CarpoolRideStartedEvent event) {
        kakaoNaviClient.estimateDuration(event.originLat(), event.originLng(), event.destLat(), event.destLng())
                .ifPresent(duration -> carpoolRideService.estimateArrival(
                        event.carpoolId(), event.startedAt().plus(duration)));
    }
}
