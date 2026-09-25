package com.ktb.moyeota.domain.companion.repository;

import com.ktb.moyeota.domain.companion.entity.Companion;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
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
}
