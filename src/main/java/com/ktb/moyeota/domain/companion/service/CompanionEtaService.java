package com.ktb.moyeota.domain.companion.service;

import com.ktb.moyeota.domain.companion.entity.Companion;
import com.ktb.moyeota.domain.companion.entity.CompanionStatus;
import com.ktb.moyeota.domain.companion.entity.StraightLineRideDuration;
import com.ktb.moyeota.domain.companion.repository.CompanionRepository;
import com.ktb.moyeota.global.external.kakao.KakaoNaviClient;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@Slf4j
@Service
public class CompanionEtaService {

    private final CompanionRepository companionRepository;
    private final KakaoNaviClient kakaoNaviClient;
    private final TransactionTemplate transactionTemplate;

    public CompanionEtaService(CompanionRepository companionRepository, KakaoNaviClient kakaoNaviClient,
                               PlatformTransactionManager transactionManager) {
        this.companionRepository = companionRepository;
        this.kakaoNaviClient = kakaoNaviClient;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    public boolean estimate(Long companionId, LocalDateTime startedAt) {
        Optional<Companion> riding = companionRepository.findById(companionId)
                .filter(companion -> companion.getStatus() == CompanionStatus.IN_PROGRESS);
        if (riding.isEmpty()) {
            return false;
        }
        Companion companion = riding.get();
        Duration duration = kakaoNaviClient.estimateDuration(
                        companion.getOriginLat(), companion.getOriginLng(), companion.getDestLat(), companion.getDestLng())
                .orElseThrow(() -> new EtaUnavailableException(companionId));
        log.info("[ETA_ESTIMATED] companionId={} naviMinutes={} straightLineMinutes={}", companionId,
                duration.toMinutes(), StraightLineRideDuration.estimate(companion.getOriginLat(),
                        companion.getOriginLng(), companion.getDestLat(), companion.getDestLng()).toMinutes());
        transactionTemplate.executeWithoutResult(status -> companionRepository.findById(companionId)
                .ifPresent(current -> current.estimateArrival(startedAt.plus(duration))));
        return true;
    }
}
