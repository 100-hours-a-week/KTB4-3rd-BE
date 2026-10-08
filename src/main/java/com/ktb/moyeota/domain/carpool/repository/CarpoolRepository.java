package com.ktb.moyeota.domain.carpool.repository;

import com.ktb.moyeota.domain.companion.entity.Companion;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CarpoolRepository extends JpaRepository<Companion, Long> {

    @Query(nativeQuery = true, value = """
            SELECT c.id AS id, c.origin_lat AS originLat, c.origin_lng AS originLng
            FROM companions c
            WHERE c.kind = 'CARPOOL'
              AND c.status = 'RECRUITING'
              AND c.departure_at > :visibleAfter
              AND MBRContains(
                    ST_GeomFromText(CONCAT('POLYGON((', :swLat, ' ', :swLng, ',', :neLat, ' ', :swLng, ',', :neLat, ' ', :neLng, ',', :swLat, ' ', :neLng, ',', :swLat, ' ', :swLng, '))'), 4326),
                    c.origin_location
                  )
            LIMIT :limit
            """)
    List<CarpoolPinProjection> findPinsInViewport(
            @Param("swLat") BigDecimal swLat,
            @Param("swLng") BigDecimal swLng,
            @Param("neLat") BigDecimal neLat,
            @Param("neLng") BigDecimal neLng,
            @Param("visibleAfter") LocalDateTime visibleAfter,
            @Param("limit") int limit);
}
