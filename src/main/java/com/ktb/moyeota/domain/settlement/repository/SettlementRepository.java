package com.ktb.moyeota.domain.settlement.repository;

import com.ktb.moyeota.domain.settlement.entity.MeterAnalysisStatus;
import com.ktb.moyeota.domain.settlement.entity.Settlement;
import jakarta.persistence.LockModeType;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

public interface SettlementRepository extends JpaRepository<Settlement, Long> {

    @Query("select s.id from Settlement s where s.companion.id = :companionId")
    Optional<Long> findIdByCompanionId(Long companionId);

    @Query("select s from Settlement s join fetch s.payee where s.companion.id = :companionId")
    Optional<Settlement> findWithPayeeByCompanionId(Long companionId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from Settlement s where s.id = :id")
    Optional<Settlement> findByIdForUpdate(Long id);

    @Query("""
            select new com.ktb.moyeota.domain.settlement.repository.MeterAnalysisAttemptProjection(
                s.id, s.analysisStartedAt)
            from Settlement s
            where s.meterAnalysisStatus = :meterAnalysisStatus and s.analysisStartedAt < :startedBefore
            """)
    List<MeterAnalysisAttemptProjection> findMeterAnalysisAttemptsStartedBefore(
            MeterAnalysisStatus meterAnalysisStatus, LocalDateTime startedBefore);
}
