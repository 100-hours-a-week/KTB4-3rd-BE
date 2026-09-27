package com.ktb.moyeota.domain.rating.repository;

import com.ktb.moyeota.domain.chat.entity.CompanionParticipant;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface RatingParticipantRepository extends JpaRepository<CompanionParticipant, Long> {

    @Query("""
            select p from CompanionParticipant p
             join fetch p.user
             where p.companion.id = :companionId
               and p.outcomeStatus = com.ktb.moyeota.domain.chat.entity.OutcomeStatus.COMPLETED
            """)
    List<CompanionParticipant> findCompletedParticipants(Long companionId);
}
