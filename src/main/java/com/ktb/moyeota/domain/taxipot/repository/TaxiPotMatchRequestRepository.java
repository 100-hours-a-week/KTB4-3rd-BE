package com.ktb.moyeota.domain.taxipot.repository;

import com.ktb.moyeota.domain.taxipot.entity.TaxiPotMatchRequest;
import com.ktb.moyeota.domain.taxipot.entity.TaxiPotMatchRequestStatus;
import jakarta.persistence.LockModeType;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

public interface TaxiPotMatchRequestRepository extends JpaRepository<TaxiPotMatchRequest, Long> {

    boolean existsByUserIdAndStatus(Long userId, TaxiPotMatchRequestStatus status);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from TaxiPotMatchRequest r where r.id in :ids order by r.id")
    List<TaxiPotMatchRequest> findAllByIdInForUpdate(Collection<Long> ids);

    @Query("select r.companion.id from TaxiPotMatchRequest r where r.id = :id")
    Optional<Long> findCompanionIdById(Long id);
}
