package com.ktb.moyeota.domain.taxipot.repository;

import com.ktb.moyeota.domain.taxipot.entity.TaxiPotRegionLease;
import java.time.LocalDateTime;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface TaxiPotRegionLeaseRepository extends JpaRepository<TaxiPotRegionLease, Long> {

    boolean existsByRegionRowAndRegionCol(Long regionRow, Long regionCol);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("""
            update TaxiPotRegionLease l
               set l.ownerId = :ownerId,
                   l.leaseUntil = :leaseUntil,
                   l.updatedAt = :now
             where l.regionRow = :regionRow
               and l.regionCol = :regionCol
               and (l.ownerId = :ownerId or l.leaseUntil < :now)
            """)
    int renew(Long regionRow, Long regionCol, String ownerId, LocalDateTime leaseUntil, LocalDateTime now);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("""
            update TaxiPotRegionLease l
               set l.leaseUntil = :now,
                   l.updatedAt = :now
             where l.ownerId = :ownerId
               and l.leaseUntil > :now
            """)
    int releaseAll(String ownerId, LocalDateTime now);
}
