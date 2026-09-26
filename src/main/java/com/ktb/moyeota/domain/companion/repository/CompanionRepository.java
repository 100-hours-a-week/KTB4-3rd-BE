package com.ktb.moyeota.domain.companion.repository;

import com.ktb.moyeota.domain.companion.entity.Companion;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

public interface CompanionRepository extends JpaRepository<Companion, Long> {

    String TAXI_POT_FOR_PARTICIPANT = """
            select c from Companion c
             where c.id = :id
               and c.kind = com.ktb.moyeota.domain.companion.entity.CompanionKind.TAXI_POT
               and exists (
                   select p from CompanionParticipant p
                    where p.companion = c
                      and p.user.id = :userId
                      and p.outcomeStatus <> com.ktb.moyeota.domain.companion.entity.ParticipantOutcome.INCOMPLETE)
            """;

    @Query(TAXI_POT_FOR_PARTICIPANT)
    Optional<Companion> findTaxiPotForParticipant(Long id, Long userId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query(TAXI_POT_FOR_PARTICIPANT)
    Optional<Companion> findTaxiPotForParticipantForUpdate(Long id, Long userId);
}
