package com.ktb.moyeota.domain.companionpost.repository;

import com.ktb.moyeota.domain.companion.entity.Companion;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CompanionPostRepository extends JpaRepository<Companion, Long> {

    @Query("""
            SELECT c FROM Companion c
            WHERE c.id = :id
              AND c.kind = com.ktb.moyeota.domain.companion.entity.CompanionKind.COMPANION
            """)
    Optional<Companion> findCompanionPostById(@Param("id") Long id);

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
