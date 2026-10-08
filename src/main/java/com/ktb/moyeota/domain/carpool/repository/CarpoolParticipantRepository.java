package com.ktb.moyeota.domain.carpool.repository;

import com.ktb.moyeota.domain.chat.entity.CompanionParticipant;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CarpoolParticipantRepository extends JpaRepository<CompanionParticipant, Long> {

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
