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

    @Query(nativeQuery = true, value = """
            SELECT * FROM (
                SELECT c.id AS id, c.origin_name AS originName, c.dest_name AS destName,
                       c.transport_type AS transportType,
                       c.current_count AS currentCount, c.capacity AS capacity,
                       c.departure_at AS departureAt, c.created_at AS createdAt, c.status AS status,
                       u.nickname AS hostNickname, u.profile_image_url AS hostProfileImageUrl,
                       ST_Distance_Sphere(c.origin_location, ST_SRID(POINT(:lng, :lat), 4326)) AS distanceM
                FROM companions c
                JOIN users u ON u.id = c.host_id
                WHERE c.kind = 'COMPANION'
                  AND c.status = 'RECRUITING'
                  AND MBRContains(
                        ST_GeomFromText(CONCAT('POLYGON((', :swLat, ' ', :swLng, ',', :neLat, ' ', :swLng, ',', :neLat, ' ', :neLng, ',', :swLat, ' ', :neLng, ',', :swLat, ' ', :swLng, '))'), 4326),
                        c.origin_location
                      )
            ) t
            WHERE :cursorDistance IS NULL
               OR t.distanceM > :cursorDistance
               OR (t.distanceM = :cursorDistance AND t.id < :cursorId)
            ORDER BY t.distanceM ASC, t.id DESC
            LIMIT :n
            """)
    List<CompanionNearbyProjection> findNearby(
            @Param("lat") BigDecimal lat,
            @Param("lng") BigDecimal lng,
            @Param("swLat") BigDecimal swLat,
            @Param("swLng") BigDecimal swLng,
            @Param("neLat") BigDecimal neLat,
            @Param("neLng") BigDecimal neLng,
            @Param("cursorDistance") Double cursorDistance,
            @Param("cursorId") Long cursorId,
            @Param("n") int n);
}
