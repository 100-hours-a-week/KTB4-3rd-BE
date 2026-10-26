package com.ktb.moyeota.domain.taxipot.service;

import com.ktb.moyeota.domain.taxipot.config.TaxiPotMatchingProperties;
import com.ktb.moyeota.domain.taxipot.entity.TaxiPotRegionLease;
import com.ktb.moyeota.domain.taxipot.matching.RegionCell;
import com.ktb.moyeota.domain.taxipot.repository.TaxiPotRegionLeaseRepository;
import jakarta.annotation.PreDestroy;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

@Slf4j
@Service
@RequiredArgsConstructor
@EnableConfigurationProperties(TaxiPotMatchingProperties.class)
public class RegionLeaseService {

    private final TaxiPotRegionLeaseRepository regionLeaseRepository;
    private final MatchingServerId serverId;
    private final TaxiPotMatchingProperties matchingProperties;
    private final TransactionTemplate transactionTemplate;
    private final Clock clock;

    public List<RegionCell> claim(List<RegionCell> regions) {
        LocalDateTime now = LocalDateTime.now(clock);
        LocalDateTime leaseUntil = now.plus(matchingProperties.leaseDuration());
        return regions.stream()
                .filter(region -> claim(region, leaseUntil, now))
                .toList();
    }

    @PreDestroy
    public void releaseAll() {
        if (!matchingProperties.isBatch()) {
            return;
        }
        try {
            Integer released = transactionTemplate.execute(
                    status -> regionLeaseRepository.releaseAll(serverId.value(), LocalDateTime.now(clock)));
            log.info("[REGION_LEASE_RELEASED] owner={}, regions={}", serverId.value(), released);
        } catch (DataAccessException e) {
            log.warn("[REGION_LEASE_RELEASE_FAILED] owner={}", serverId.value(), e);
        }
    }

    private boolean claim(RegionCell region, LocalDateTime leaseUntil, LocalDateTime now) {
        Integer renewed = transactionTemplate.execute(status -> regionLeaseRepository.renew(
                region.row(), region.col(), serverId.value(), leaseUntil, now));
        if (renewed != null && renewed == 1) {
            return true;
        }
        if (regionLeaseRepository.existsByRegionRowAndRegionCol(region.row(), region.col())) {
            return false;
        }
        try {
            transactionTemplate.executeWithoutResult(status -> regionLeaseRepository.saveAndFlush(
                    TaxiPotRegionLease.claim(region, serverId.value(), leaseUntil, now)));
            return true;
        } catch (DataIntegrityViolationException e) {
            return false;
        }
    }
}
