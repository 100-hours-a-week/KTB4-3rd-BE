package com.ktb.moyeota.domain.taxipot.repository;

import com.ktb.moyeota.domain.chat.entity.CompanionParticipant;
import com.ktb.moyeota.domain.companion.entity.Companion;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface TaxiPotParticipantRepository extends JpaRepository<CompanionParticipant, Long> {

    @Query("""
            select p.companion from CompanionParticipant p
             where p.user.id = :userId
               and p.outcomeStatus = com.ktb.moyeota.domain.chat.entity.OutcomeStatus.PENDING
               and p.companion.kind = com.ktb.moyeota.domain.companion.entity.CompanionKind.TAXI_POT
            """)
    Optional<Companion> findCurrentTaxiPot(Long userId);

    Optional<CompanionParticipant> findByCompanionIdAndUserId(Long companionId, Long userId);

    @Query("""
            select p from CompanionParticipant p
             where p.companion.id = :companionId
               and p.user.id <> :leaverId
               and p.outcomeStatus = com.ktb.moyeota.domain.chat.entity.OutcomeStatus.PENDING
             order by p.joinedAt, p.id
             limit 1
            """)
    Optional<CompanionParticipant> findNextHost(Long companionId, Long leaverId);
}
