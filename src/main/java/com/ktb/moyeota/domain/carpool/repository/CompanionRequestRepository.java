package com.ktb.moyeota.domain.carpool.repository;

import com.ktb.moyeota.domain.carpool.entity.CompanionRequest;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CompanionRequestRepository extends JpaRepository<CompanionRequest, Long> {

    Optional<CompanionRequest> findFirstByCompanionIdAndRequesterIdOrderByIdDesc(Long companionId, Long requesterId);
}
