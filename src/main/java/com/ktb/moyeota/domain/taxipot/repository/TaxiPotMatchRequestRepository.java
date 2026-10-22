package com.ktb.moyeota.domain.taxipot.repository;

import com.ktb.moyeota.domain.taxipot.entity.TaxiPotMatchRequest;
import com.ktb.moyeota.domain.taxipot.entity.TaxiPotMatchRequestStatus;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TaxiPotMatchRequestRepository extends JpaRepository<TaxiPotMatchRequest, Long> {

    boolean existsByUserIdAndStatus(Long userId, TaxiPotMatchRequestStatus status);
}
