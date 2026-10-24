package com.ktb.moyeota.domain.taxipot.repository;

import com.ktb.moyeota.domain.taxipot.entity.TaxiPotMatchRequest;
import com.ktb.moyeota.domain.taxipot.entity.TaxiPotMatchRequestStatus;
import jakarta.persistence.LockModeType;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface TaxiPotMatchRequestRepository extends JpaRepository<TaxiPotMatchRequest, Long> {

    boolean existsByUserIdAndStatus(Long userId, TaxiPotMatchRequestStatus status);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from TaxiPotMatchRequest r where r.id in :ids order by r.id")
    List<TaxiPotMatchRequest> findAllByIdInForUpdate(Collection<Long> ids);

    @Query("select r.companion.id from TaxiPotMatchRequest r where r.id = :id")
    Optional<Long> findCompanionIdById(Long id);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("""
            update TaxiPotMatchRequest r
               set r.status = com.ktb.moyeota.domain.taxipot.entity.TaxiPotMatchRequestStatus.FAILED,
                   r.updatedAt = :now
             where r.status = com.ktb.moyeota.domain.taxipot.entity.TaxiPotMatchRequestStatus.MATCHING
               and r.departureAt < :cutoff
            """)
    int failDeparted(LocalDateTime cutoff, LocalDateTime now);

    @Query("""
            select distinct new com.ktb.moyeota.domain.taxipot.repository.MatchingRegion(r.regionRow, r.regionCol)
              from TaxiPotMatchRequest r
             where r.status = com.ktb.moyeota.domain.taxipot.entity.TaxiPotMatchRequestStatus.MATCHING
            """)
    List<MatchingRegion> findMatchingRegions();

    @Query("""
            select r from TaxiPotMatchRequest r
             where r.status = com.ktb.moyeota.domain.taxipot.entity.TaxiPotMatchRequestStatus.MATCHING
               and r.regionRow = :regionRow
               and r.regionCol = :regionCol
             order by r.id
            """)
    List<TaxiPotMatchRequest> findMatchingInRegion(Long regionRow, Long regionCol);

    @Query("""
            select r from TaxiPotMatchRequest r
              join fetch r.companion c
             where r.status = com.ktb.moyeota.domain.taxipot.entity.TaxiPotMatchRequestStatus.MATCHED
               and r.regionRow = :regionRow
               and r.regionCol = :regionCol
               and c.kind = com.ktb.moyeota.domain.companion.entity.CompanionKind.TAXI_POT
               and c.status = com.ktb.moyeota.domain.companion.entity.CompanionStatus.RECRUITING
               and c.currentCount < c.capacity
               and c.departureAt >= :cutoff
               and exists (
                   select p from CompanionParticipant p
                    where p.companion = c
                      and p.user = r.user
                      and p.outcomeStatus = com.ktb.moyeota.domain.chat.entity.OutcomeStatus.PENDING)
             order by c.id, r.id
            """)
    List<TaxiPotMatchRequest> findOpenPotMembersInRegion(Long regionRow, Long regionCol, LocalDateTime cutoff);
}
