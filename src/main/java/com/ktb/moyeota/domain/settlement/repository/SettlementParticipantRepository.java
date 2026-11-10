package com.ktb.moyeota.domain.settlement.repository;

import com.ktb.moyeota.domain.settlement.entity.SettlementParticipant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface SettlementParticipantRepository extends JpaRepository<SettlementParticipant, Long> {

    Optional<SettlementParticipant> findBySettlementIdAndUserId(Long settlementId, Long userId);

    List<SettlementParticipant> findAllBySettlementId(Long settlementId);

    @Query("""
            select p from SettlementParticipant p
             join fetch p.user
             where p.settlement.id = :settlementId
             order by p.id
            """)
    List<SettlementParticipant> findAllWithUserBySettlementId(Long settlementId);
}
