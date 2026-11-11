package com.ktb.moyeota.domain.carpool.repository;

import com.ktb.moyeota.domain.carpool.entity.CompanionRequest;
import com.ktb.moyeota.domain.carpool.entity.CompanionRequestStatus;
import com.ktb.moyeota.domain.carpool.entity.RejectReason;
import jakarta.persistence.LockModeType;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CompanionRequestRepository extends JpaRepository<CompanionRequest, Long> {

    Optional<CompanionRequest> findFirstByCompanionIdAndRequesterIdOrderByIdDesc(Long companionId, Long requesterId);

    boolean existsByCompanionIdAndRequesterIdAndStatus(
            Long companionId, Long requesterId, CompanionRequestStatus status);

    long countByCompanionIdAndRequesterIdAndRejectReason(
            Long companionId, Long requesterId, RejectReason rejectReason);

    @Query("""
            select r from CompanionRequest r
              join fetch r.requester
             where r.companion.id = :companionId
               and r.status = com.ktb.moyeota.domain.carpool.entity.CompanionRequestStatus.PENDING
             order by r.id
            """)
    List<CompanionRequest> findPendingWithRequester(@Param("companionId") Long companionId);

    @Query("""
            select distinct c.id from CompanionRequest r
              join r.companion c
             where c.kind = com.ktb.moyeota.domain.companion.entity.CompanionKind.CARPOOL
               and c.departureAt < :now
               and r.status = com.ktb.moyeota.domain.carpool.entity.CompanionRequestStatus.PENDING
             order by c.id
            """)
    List<Long> findDepartedCarpoolIdsWithPending(@Param("now") LocalDateTime now, Pageable pageable);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<CompanionRequest> findForUpdateByIdAndCompanionId(Long id, Long companionId);

    @Query("""
            select r from CompanionRequest r
              join fetch r.requester
             where r.id = :id
               and r.companion.id = :companionId
            """)
    Optional<CompanionRequest> findWithRequester(@Param("id") Long id, @Param("companionId") Long companionId);

    @Query("""
            select r from CompanionRequest r
              join fetch r.companion c
              join fetch c.host
             where r.requester.id = :userId
               and (:cursorId is null or r.id < :cursorId)
             order by r.id desc
            """)
    List<CompanionRequest> findSent(
            @Param("userId") Long userId, @Param("cursorId") Long cursorId, Pageable pageable);

    @Query("""
            select r from CompanionRequest r
              join fetch r.companion c
              join fetch r.requester u
             where c.host.id = :userId
               and r.status = com.ktb.moyeota.domain.carpool.entity.CompanionRequestStatus.PENDING
               and c.status = com.ktb.moyeota.domain.companion.entity.CompanionStatus.RECRUITING
               and c.departureAt >= :now
               and u.withdrawnAt is null
               and (:cursorId is null or r.id < :cursorId)
             order by r.id desc
            """)
    List<CompanionRequest> findReceivable(
            @Param("userId") Long userId,
            @Param("now") LocalDateTime now,
            @Param("cursorId") Long cursorId,
            Pageable pageable);
}
