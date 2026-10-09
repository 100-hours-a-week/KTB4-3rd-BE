package com.ktb.moyeota.domain.carpool.repository;

import com.ktb.moyeota.domain.companion.entity.Companion;
import jakarta.persistence.LockModeType;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CarpoolRepository extends JpaRepository<Companion, Long> {

    @Query("""
            select c from Companion c
             where c.id = :id
               and c.kind = com.ktb.moyeota.domain.companion.entity.CompanionKind.CARPOOL
            """)
    Optional<Companion> findCarpool(@Param("id") Long id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select c from Companion c
             where c.id = :id
               and c.kind = com.ktb.moyeota.domain.companion.entity.CompanionKind.CARPOOL
            """)
    Optional<Companion> findCarpoolForUpdate(@Param("id") Long id);

    @Query("""
            select c.id from Companion c
             where c.kind = com.ktb.moyeota.domain.companion.entity.CompanionKind.CARPOOL
               and c.status = com.ktb.moyeota.domain.companion.entity.CompanionStatus.RECRUITING
               and c.currentCount >= 2
               and c.departureAt <= :now
               and not exists (
                   select m from Message m
                    where m.chatRoom.companion = c
                      and m.messageType = com.ktb.moyeota.domain.chat.entity.MessageType.SYSTEM_RIDE_START_REQUESTED)
            """)
    List<Long> findRideStartDueIds(@Param("now") LocalDateTime now);

    @Query("""
            select c.id from Companion c
             where c.kind = com.ktb.moyeota.domain.companion.entity.CompanionKind.CARPOOL
               and c.status = com.ktb.moyeota.domain.companion.entity.CompanionStatus.IN_PROGRESS
               and c.etaAt <= :now
               and not exists (
                   select m from Message m
                    where m.chatRoom.companion = c
                      and m.messageType = com.ktb.moyeota.domain.chat.entity.MessageType.SYSTEM_RIDE_END_REQUESTED)
            """)
    List<Long> findRideEndDueIds(@Param("now") LocalDateTime now);

    @Query("""
            select c.id from Companion c
             where c.kind = com.ktb.moyeota.domain.companion.entity.CompanionKind.CARPOOL
               and c.status = com.ktb.moyeota.domain.companion.entity.CompanionStatus.RECRUITING
               and c.departureAt < :deadline
            """)
    List<Long> findAutoCancelDueIds(@Param("deadline") LocalDateTime deadline);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select c from Companion c
             where c.id = :id
               and c.kind = com.ktb.moyeota.domain.companion.entity.CompanionKind.CARPOOL
               and c.status = com.ktb.moyeota.domain.companion.entity.CompanionStatus.RECRUITING
            """)
    Optional<Companion> findRecruitingCarpoolForUpdate(@Param("id") Long id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select c from Companion c
             where c.id = :id
               and c.kind = com.ktb.moyeota.domain.companion.entity.CompanionKind.CARPOOL
               and exists (
                   select p from CompanionParticipant p
                    where p.companion = c
                      and p.user.id = :userId
                      and p.outcomeStatus <> com.ktb.moyeota.domain.chat.entity.OutcomeStatus.INCOMPLETE)
            """)
    Optional<Companion> findCarpoolForParticipantForUpdate(@Param("id") Long id, @Param("userId") Long userId);

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

    @Query(nativeQuery = true, value = """
            SELECT * FROM (
                SELECT c.id AS id, u.name AS hostName, u.profile_image_url AS hostProfileImageUrl,
                       c.origin_name AS originName, c.dest_name AS destName, c.departure_at AS departureAt,
                       c.current_count AS currentCount, c.capacity AS capacity,
                       ST_Distance_Sphere(c.origin_location, ST_SRID(POINT(:lng, :lat), 4326)) AS distanceM
                FROM companions c
                JOIN users u ON u.id = c.host_id
                WHERE c.kind = 'CARPOOL'
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
            LIMIT :limit
            """)
    List<NearbyCarpoolProjection> findNearby(
            @Param("lat") BigDecimal lat,
            @Param("lng") BigDecimal lng,
            @Param("swLat") BigDecimal swLat,
            @Param("swLng") BigDecimal swLng,
            @Param("neLat") BigDecimal neLat,
            @Param("neLng") BigDecimal neLng,
            @Param("cursorDistance") Double cursorDistance,
            @Param("cursorId") Long cursorId,
            @Param("limit") int limit);
}
