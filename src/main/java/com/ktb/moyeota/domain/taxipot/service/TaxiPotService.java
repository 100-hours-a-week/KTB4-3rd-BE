package com.ktb.moyeota.domain.taxipot.service;

import com.ktb.moyeota.domain.companion.entity.Companion;
import com.ktb.moyeota.domain.companion.repository.CompanionParticipantRepository;
import com.ktb.moyeota.domain.taxipot.model.CurrentTaxiPot;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class TaxiPotService {

    private final CompanionParticipantRepository companionParticipantRepository;

    @Transactional(readOnly = true)
    public Optional<CurrentTaxiPot> findMyCurrent(Long userId) {
        return companionParticipantRepository.findCurrentTaxiPot(userId).map(TaxiPotService::toCurrentTaxiPot);
    }

    private static CurrentTaxiPot toCurrentTaxiPot(Companion companion) {
        return new CurrentTaxiPot(
                companion.getId(), companion.getStatus(), companion.getCurrentCount(), companion.getCapacity());
    }
}
