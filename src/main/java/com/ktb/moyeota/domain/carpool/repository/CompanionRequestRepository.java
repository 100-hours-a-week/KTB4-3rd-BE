package com.ktb.moyeota.domain.carpool.repository;

import com.ktb.moyeota.domain.carpool.entity.CompanionRequest;
import com.ktb.moyeota.domain.carpool.entity.CompanionRequestStatus;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

public interface CompanionRequestRepository extends JpaRepository<CompanionRequest, Long> {

    Optional<CompanionRequest> findFirstByCompanionIdAndRequesterIdOrderByIdDesc(Long companionId, Long requesterId);

    boolean existsByCompanionIdAndRequesterIdAndStatus(
            Long companionId, Long requesterId, CompanionRequestStatus status);

    long countByCompanionIdAndRequesterId(Long companionId, Long requesterId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<CompanionRequest> findForUpdateByIdAndCompanionId(Long id, Long companionId);
}
