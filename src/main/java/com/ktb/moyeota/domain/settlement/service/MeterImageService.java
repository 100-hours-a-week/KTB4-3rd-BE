package com.ktb.moyeota.domain.settlement.service;

import com.ktb.moyeota.domain.image.model.ImagePurpose;
import com.ktb.moyeota.domain.image.model.UploadScope;
import com.ktb.moyeota.domain.image.service.ImagePromotionService;
import com.ktb.moyeota.domain.settlement.entity.Settlement;
import com.ktb.moyeota.domain.settlement.error.SettlementErrorCode;
import com.ktb.moyeota.domain.settlement.model.MeterAnalysis;
import com.ktb.moyeota.domain.settlement.repository.SettlementRepository;
import com.ktb.moyeota.domain.settlement.repository.SettlementRiderRepository;
import com.ktb.moyeota.domain.taxipot.error.TaxiPotErrorCode;
import com.ktb.moyeota.global.exception.BusinessException;
import java.time.Clock;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

@Service
@RequiredArgsConstructor
public class MeterImageService {

    private final SettlementRepository settlementRepository;
    private final SettlementRiderRepository settlementRiderRepository;
    private final ImagePromotionService imagePromotionService;
    private final TransactionTemplate transactionTemplate;
    private final Clock clock;

    public MeterAnalysis submit(Long userId, Long settlementId, String tmpKey) {
        transactionTemplate.executeWithoutResult(status -> findForHost(userId, settlementId).checkMeterSubmittable());
        String meterImageKey = imagePromotionService.promote(UploadScope.user(userId), ImagePurpose.METER, tmpKey);
        return transactionTemplate.execute(status -> {
            Settlement settlement = settlementRepository.findByIdForUpdate(settlementId)
                    .orElseThrow(() -> new BusinessException(SettlementErrorCode.SETTLEMENT_NOT_FOUND));
            settlement.submitMeter(meterImageKey, LocalDateTime.now(clock));
            return MeterAnalysis.from(settlement);
        });
    }

    private Settlement findForHost(Long userId, Long settlementId) {
        Settlement settlement = settlementRepository.findById(settlementId)
                .filter(found -> settlementRiderRepository.existsRider(found.getCompanion().getId(), userId))
                .orElseThrow(() -> new BusinessException(SettlementErrorCode.SETTLEMENT_NOT_FOUND));
        if (!settlement.isPaidTo(userId)) {
            throw new BusinessException(TaxiPotErrorCode.HOST_ONLY);
        }
        return settlement;
    }
}
