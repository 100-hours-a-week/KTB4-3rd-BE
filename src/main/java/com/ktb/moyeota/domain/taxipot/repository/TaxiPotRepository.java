package com.ktb.moyeota.domain.taxipot.repository;

import com.ktb.moyeota.domain.companion.entity.Companion;
import jakarta.persistence.LockModeType;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

public interface TaxiPotRepository extends JpaRepository<Companion, Long> {

    String TAXI_POT_FOR_PARTICIPANT = """
            select c from Companion c
             where c.id = :id
               and c.kind = com.ktb.moyeota.domain.companion.entity.CompanionKind.TAXI_POT
               and exists (
                   select p from CompanionParticipant p
                    where p.companion = c
                      and p.user.id = :userId
                      and p.outcomeStatus <> com.ktb.moyeota.domain.chat.entity.OutcomeStatus.INCOMPLETE)
            """;

    @Query(TAXI_POT_FOR_PARTICIPANT)
    Optional<Companion> findTaxiPotForParticipant(Long id, Long userId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query(TAXI_POT_FOR_PARTICIPANT)
    Optional<Companion> findTaxiPotForParticipantForUpdate(Long id, Long userId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select c from Companion c
             where c.kind = com.ktb.moyeota.domain.companion.entity.CompanionKind.TAXI_POT
               and c.status = com.ktb.moyeota.domain.companion.entity.CompanionStatus.RECRUITING
               and c.currentCount < c.capacity
               and c.originLat = :originLat
               and c.originLng = :originLng
               and c.destLat = :destLat
               and c.destLng = :destLng
               and c.departureAt = :departureAt
             order by c.id
             limit 1
            """)
    Optional<Companion> findMatchableTaxiPotForUpdate(
            BigDecimal originLat, BigDecimal originLng, BigDecimal destLat, BigDecimal destLng,
            LocalDateTime departureAt);
}
