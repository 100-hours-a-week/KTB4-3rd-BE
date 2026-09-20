package com.ktb.moyeota.domain.companionpost.repository;

import com.ktb.moyeota.domain.companion.entity.Companion;
import java.math.BigDecimal;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CompanionPostRepository extends JpaRepository<Companion, Long> {

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

    /**
     * companion_participants.user_id 목록. companions와 같은 "동행" 데이터라 별도 도메인이
     * 아니라 이 companionpost 레포지토리에서 그대로 제어한다(핀 조회와 동일한 방식 — Entity 없이
     * native query + companion_id로 직접 조회).
     * left_at IS NULL로 걸러서 "현재 참여 중"인 사람만 반환 — 이 필터는 스펙에 명시는 안 돼 있고
     * 내가 판단해서 넣은 거라 맞는지 확인 필요.
     */
    @Query(nativeQuery = true, value = """
            SELECT cp.user_id
            FROM companion_participants cp
            WHERE cp.companion_id = :companionId
              AND cp.left_at IS NULL
            """)
    List<Long> findParticipantUserIds(@Param("companionId") Long companionId);
}
