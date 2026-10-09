package com.ktb.moyeota.domain.carpool.repository;

import com.ktb.moyeota.domain.chat.entity.CompanionParticipant;
import com.ktb.moyeota.domain.chat.entity.OutcomeStatus;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CarpoolParticipantRepository extends JpaRepository<CompanionParticipant, Long> {

    Optional<CompanionParticipant> findByCompanionIdAndUserId(Long companionId, Long userId);

    boolean existsByCompanionIdAndUserIdAndOutcomeStatus(Long companionId, Long userId, OutcomeStatus outcomeStatus);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<CompanionParticipant> findForUpdateByCompanionIdAndUserId(Long companionId, Long userId);

    @Query("""
            select p from CompanionParticipant p
             where p.companion.id = :carpoolId
               and p.outcomeStatus = com.ktb.moyeota.domain.chat.entity.OutcomeStatus.PENDING
            """)
    List<CompanionParticipant> findPendingParticipants(@Param("carpoolId") Long carpoolId);

    @Query("""
            select p from CompanionParticipant p
              join fetch p.user
             where p.companion.id = :carpoolId
               and p.outcomeStatus <> com.ktb.moyeota.domain.chat.entity.OutcomeStatus.INCOMPLETE
             order by p.joinedAt asc, p.id asc
            """)
    List<CompanionParticipant> findRiders(@Param("carpoolId") Long carpoolId);
}
