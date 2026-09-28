package com.ktb.moyeota.domain.community.repository;

import com.ktb.moyeota.domain.community.entity.CommunityPost;
import java.math.BigDecimal;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CommunityPostRepository extends JpaRepository<CommunityPost, Long> {

    @Query(nativeQuery = true, value = """
            SELECT cp.id AS id, cp.title AS title, cp.lat AS lat, cp.lng AS lng
            FROM community_posts cp
            WHERE cp.deleted_at IS NULL
              AND MBRContains(
                    ST_GeomFromText(CONCAT('POLYGON((', :swLat, ' ', :swLng, ',', :neLat, ' ', :swLng, ',', :neLat, ' ', :neLng, ',', :swLat, ' ', :neLng, ',', :swLat, ' ', :swLng, '))'), 4326),
                    cp.location
                  )
            """)
    List<CommunityPinProjection> findPinsInViewport(
            @Param("swLat") BigDecimal swLat,
            @Param("swLng") BigDecimal swLng,
            @Param("neLat") BigDecimal neLat,
            @Param("neLng") BigDecimal neLng);


    @Modifying
    @Query("UPDATE CommunityPost p SET p.commentCount = p.commentCount + 1 WHERE p.id = :postId")
    int increaseCommentCount(@Param("postId") Long postId);

    @Query(nativeQuery = true, value = """
            SELECT * FROM (
                SELECT cp.id AS id, cp.title AS title, cp.comment_count AS commentCount,
                       cp.created_at AS createdAt,
                       u.nickname AS authorNickname, u.profile_image_url AS authorProfileImageUrl,
                       ST_Distance_Sphere(cp.location, ST_SRID(POINT(:lng, :lat), 4326)) AS distanceM
                FROM community_posts cp
                JOIN users u ON u.id = cp.author_id
                WHERE cp.deleted_at IS NULL
                  AND MBRContains(
                        ST_GeomFromText(CONCAT('POLYGON((', :swLat, ' ', :swLng, ',', :neLat, ' ', :swLng, ',', :neLat, ' ', :neLng, ',', :swLat, ' ', :neLng, ',', :swLat, ' ', :swLng, '))'), 4326),
                        cp.location
                      )
            ) t
            WHERE :cursorDistance IS NULL
               OR t.distanceM > :cursorDistance
               OR (t.distanceM = :cursorDistance AND t.id < :cursorId)
            ORDER BY t.distanceM ASC, t.id DESC
            LIMIT :n
            """)
    List<CommunityNearbyProjection> findNearby(
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
