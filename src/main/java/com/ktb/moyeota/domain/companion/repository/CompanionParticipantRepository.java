package com.ktb.moyeota.domain.companion.repository;

import com.ktb.moyeota.domain.companion.entity.Companion;
import com.ktb.moyeota.domain.companion.entity.CompanionParticipant;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface CompanionParticipantRepository extends JpaRepository<CompanionParticipant, Long> {

    @Query("""
            select p.companion from CompanionParticipant p
             where p.user.id = :userId
               and p.outcomeStatus = com.ktb.moyeota.domain.companion.entity.ParticipantOutcome.PENDING
               and p.companion.kind = com.ktb.moyeota.domain.companion.entity.CompanionKind.TAXI_POT
            """)
    Optional<Companion> findCurrentTaxiPot(Long userId);
}
