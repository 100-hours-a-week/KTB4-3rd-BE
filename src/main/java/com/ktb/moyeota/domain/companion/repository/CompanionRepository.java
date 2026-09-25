package com.ktb.moyeota.domain.companion.repository;

import com.ktb.moyeota.domain.companion.entity.Companion;
import java.time.LocalDateTime;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface CompanionRepository extends JpaRepository<Companion, Long> {

    @Query("""
            select c from Companion c
             where c.id = :id
               and c.kind = com.ktb.moyeota.domain.companion.entity.CompanionKind.TAXI_POT
               and exists (
                   select p from CompanionParticipant p
                    where p.companion = c
                      and p.user.id = :userId
                      and p.outcomeStatus <> com.ktb.moyeota.domain.companion.entity.ParticipantOutcome.INCOMPLETE)
            """)
    Optional<Companion> findTaxiPotForParticipant(Long id, Long userId);

    @Modifying
    @Query("""
            update Companion c
               set c.status = com.ktb.moyeota.domain.companion.entity.CompanionStatus.IN_PROGRESS
             where c.id = :id
               and c.kind = com.ktb.moyeota.domain.companion.entity.CompanionKind.TAXI_POT
               and c.status = com.ktb.moyeota.domain.companion.entity.CompanionStatus.RECRUITING
               and c.host.id = :hostId
               and c.currentCount >= 2
               and c.departureAt <= :now
            """)
    int startRide(Long id, Long hostId, LocalDateTime now);

    @Modifying
    @Query("""
            update Companion c
               set c.status = com.ktb.moyeota.domain.companion.entity.CompanionStatus.COMPLETED
             where c.id = :id
               and c.kind = com.ktb.moyeota.domain.companion.entity.CompanionKind.TAXI_POT
               and c.status = com.ktb.moyeota.domain.companion.entity.CompanionStatus.IN_PROGRESS
               and c.host.id = :hostId
            """)
    int completeRide(Long id, Long hostId);
}
