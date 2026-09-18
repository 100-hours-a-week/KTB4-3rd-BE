package com.ktb.moyeota.domain.companion.repository;

import com.ktb.moyeota.domain.companion.entity.Companion;
import java.math.BigDecimal;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CompanionRepository extends JpaRepository<Companion, Long> {

    // save(Companion), findById(Long)은 JpaRepository가 기본 제공.

    /**
     * 지도 핀(map-pins) 조회 — companion 도메인 쪽 조회 로직.
     * community의 CompanionFeedQueryPort 구현체(companionpost 도메인의 어댑터)가 내부에서 사용한다.
     * kind = 'COMPANION'(동행모집)이면서 RECRUITING 상태인 것만, origin_location 기준 SPATIAL INDEX(MBRContains)로 조회.
     */
    @Query(nativeQuery = true, value = """
            SELECT c.id AS id, c.origin_name AS originName, c.dest_name AS destName,
                   c.origin_lat AS originLat, c.origin_lng AS originLng
            FROM companions c
            WHERE c.kind = 'COMPANION'
              AND c.status = 'RECRUITING'
              AND MBRContains(
                    ST_GeomFromText(CONCAT('POLYGON((', :swLat, ' ', :swLng, ',', :neLat, ' ', :swLng, ',', :neLat, ' ', :neLng, ',', :swLat, ' ', :neLng, ',', :swLat, ' ', :swLng, '))'), 4326),
                    c.origin_location
                  )
            """)
    List<CompanionPinProjection> findPinsInViewport(
            @Param("swLat") BigDecimal swLat,
            @Param("swLng") BigDecimal swLng,
            @Param("neLat") BigDecimal neLat,
            @Param("neLng") BigDecimal neLng);
}
