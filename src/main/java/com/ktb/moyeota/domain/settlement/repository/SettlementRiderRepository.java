package com.ktb.moyeota.domain.settlement.repository;

import com.ktb.moyeota.domain.chat.entity.CompanionParticipant;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface SettlementRiderRepository extends JpaRepository<CompanionParticipant, Long> {

    @Query("""
            select p from CompanionParticipant p
             join fetch p.user
             where p.companion.id = :companionId
               and p.outcomeStatus <> com.ktb.moyeota.domain.chat.entity.OutcomeStatus.INCOMPLETE
            """)
    List<CompanionParticipant> findRiders(Long companionId);

    @Query("""
            select count(p) > 0 from CompanionParticipant p
             where p.companion.id = :companionId
               and p.user.id = :userId
               and p.outcomeStatus <> com.ktb.moyeota.domain.chat.entity.OutcomeStatus.INCOMPLETE
            """)
    boolean existsRider(Long companionId, Long userId);
}
