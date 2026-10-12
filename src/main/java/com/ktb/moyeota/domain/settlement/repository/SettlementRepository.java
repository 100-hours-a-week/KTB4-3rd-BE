package com.ktb.moyeota.domain.settlement.repository;

import com.ktb.moyeota.domain.settlement.entity.Settlement;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

public interface SettlementRepository extends JpaRepository<Settlement, Long> {

    @Query("select s.id from Settlement s where s.companion.id = :companionId")
    Optional<Long> findIdByCompanionId(Long companionId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from Settlement s where s.id = :id")
    Optional<Settlement> findByIdForUpdate(Long id);
}
