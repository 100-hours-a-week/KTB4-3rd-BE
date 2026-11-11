package com.ktb.moyeota.domain.carpool.scheduler;

import com.ktb.moyeota.domain.carpool.service.CarpoolRequestService;
import com.ktb.moyeota.global.config.ConditionalOnServerRole;
import com.ktb.moyeota.global.config.ServerRole;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 출발 시각이 지난 카풀에 남은 참여 요청(companion_requests.status = PENDING)을 정리한다.
 * 카풀마다 별도 트랜잭션으로 처리하고, 한 카풀이 실패해도 다음 카풀은 계속 처리한다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnServerRole({ServerRole.WORKER, ServerRole.ALL})
public class CarpoolRequestExpireScheduler {

    private final CarpoolRequestService carpoolRequestService;

    @Scheduled(fixedDelayString = "${moyeota.carpool.request-expire-sweep-delay}")
    public void sweep() {
        for (Long carpoolId : carpoolRequestService.findExpireDueIds()) {
            try {
                carpoolRequestService.expireDepartedRequests(carpoolId);
            } catch (RuntimeException e) {
                log.warn("[CARPOOL_REQUEST_EXPIRE_FAILED] carpoolId={}", carpoolId, e);
            }
        }
    }
}
