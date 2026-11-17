package com.ktb.moyeota.domain.carpool.service;

import com.ktb.moyeota.domain.carpool.event.CarpoolRideStartedEvent;
import com.ktb.moyeota.domain.companion.entity.StraightLineRideDuration;
import com.ktb.moyeota.global.external.kakao.KakaoNaviClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "moyeota.chat.system-message", name = "mode", havingValue = "direct",
        matchIfMissing = true)
public class CarpoolEtaRefresher {

    private final KakaoNaviClient kakaoNaviClient;
    private final CarpoolRideService carpoolRideService;

    @Async
    @TransactionalEventListener
    public void refresh(CarpoolRideStartedEvent event) {
        kakaoNaviClient.estimateDuration(event.originLat(), event.originLng(), event.destLat(), event.destLng())
                .ifPresent(duration -> {
                    log.info("[ETA_ESTIMATED] carpoolId={} naviMinutes={} straightLineMinutes={}",
                            event.carpoolId(), duration.toMinutes(), StraightLineRideDuration.estimate(
                                    event.originLat(), event.originLng(), event.destLat(), event.destLng()).toMinutes());
                    carpoolRideService.estimateArrival(event.carpoolId(), event.startedAt().plus(duration));
                });
    }
}
